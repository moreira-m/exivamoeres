package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A corrida pela última vaga — a lacuna nº 2 do {@code TESTS.md} §5.
 *
 * O lock pessimista de {@code HuntingListRepository.findByIdForUpdate} existe
 * <b>exatamente</b> para isto e nunca havia sido exercitado: um teste de uma
 * thread por vez passa igual com e sem ele, porque o defeito só aparece quando
 * duas transações leem a contagem de membros <b>antes</b> de qualquer uma
 * gravar. As duas veem "cabe mais um" e o time termina com 6 membros num limite
 * de 5 — um estado que nenhuma tela consegue explicar e que nada no sistema
 * corrige depois.
 *
 * <p><b>Por que o time é enchido na mão</b> em vez de baixar
 * {@code app.team.max-members} por {@code @TestPropertySource}: propriedade
 * diferente = <b>contexto Spring novo</b> = mais um pool de conexões contra o
 * mesmo Postgres do Testcontainers. A primeira versão deste teste fazia isso e
 * derrubou <b>outra</b> classe com {@code FATAL: sorry, too many clients
 * already} — um teste que reprova o vizinho é pior que a lacuna que ele fecha.
 * Encher o time custa três entradas de preparação e nenhum contexto.</p>
 *
 * <p>Os dois caminhos que travam a linha do time são cobertos: a <b>entrada
 * automática</b> ({@code joinByShareCode} com {@code AUTO_ACCEPT}) e a
 * <b>aprovação pelo dono</b> ({@code approveJoinRequest}) — a mesma regra de
 * vaga, por portas diferentes.</p>
 */
class SlotRaceIntegrationTest extends TeamIntegrationTestBase {

    /** Igual ao {@code app.team.max-members} de produção (application.yml). */
    private static final int MAX_MEMBROS = 5;

    @Autowired HuntingListService listService;
    @Autowired ListMembershipRepository membershipRepository;

    @Test
    void duasEntradasSimultaneasNaUltimaVagaDeixamOTimeComExatamenteUmNovoMembro() {
        String world = "RaceJoinWorld";
        Ctx time = timeCheioMenosUmaVaga(world, "RaceJoin", JoinPolicy.AUTO_ACCEPT);
        Long candidatoA = candidato("race-join-a", "Race Join A", world);
        Long candidatoB = candidato("race-join-b", "Race Join B", world);

        List<Resultado> resultados = aoMesmoTempo(
                () -> listService.joinByShareCode(candidatoA, time.shareCode,
                        new JoinListRequest(personagemDe(candidatoA))),
                () -> listService.joinByShareCode(candidatoB, time.shareCode,
                        new JoinListRequest(personagemDe(candidatoB))));

        // Exatamente um entra. Sem o lock, os dois entram: as duas transações
        // contam 4 aprovados num limite de 5 e concluem que cabem — e o time
        // termina com 6.
        assertThat(resultados).filteredOn(Resultado::deuCerto).hasSize(1);
        assertThat(aprovados(time.listId)).isEqualTo(MAX_MEMBROS);

        // E o que perdeu recebe a recusa de regra de negócio (422), não um erro
        // de banco vazando como 500.
        Resultado perdedor = resultados.stream().filter(r -> !r.deuCerto()).findFirst().orElseThrow();
        assertThat(perdedor.erro).isInstanceOf(BusinessRuleException.class);
        assertThat(perdedor.erro).hasMessageContaining("cheio");
    }

    @Test
    void duasAprovacoesSimultaneasNaUltimaVagaAprovamExatamenteUma() {
        String world = "RaceApproveWorld";
        Ctx time = timeCheioMenosUmaVaga(world, "RaceApprove", JoinPolicy.MANUAL_APPROVAL);
        Long pedidoA = pedidoPendente(time, "race-approve-a", "Race Approve A", world);
        Long pedidoB = pedidoPendente(time, "race-approve-b", "Race Approve B", world);

        List<Resultado> resultados = aoMesmoTempo(
                () -> listService.approveJoinRequest(time.ownerId, time.listId, pedidoA),
                () -> listService.approveJoinRequest(time.ownerId, time.listId, pedidoB));

        // Pedido não reserva vaga (cinco pedidos travariam o time), então dois
        // cliques quase simultâneos do dono são o caminho natural para o furo.
        assertThat(resultados).filteredOn(Resultado::deuCerto).hasSize(1);
        assertThat(aprovados(time.listId)).isEqualTo(MAX_MEMBROS);
        // O pedido que perdeu continua PENDING: recusa por falta de vaga não é
        // "o dono não quis", e o dono tenta de novo quando alguém sair.
        assertThat(membershipRepository.findAllByListIdAndActiveTrue(time.listId))
                .filteredOn(m -> m.getStatus() == MembershipStatus.PENDING)
                .hasSize(1);
    }

    // ---------------------------------------------------------------- infra

    /**
     * Roda as duas chamadas de verdade em paralelo, cada uma na sua transação, e
     * solta as duas no mesmo instante.
     *
     * A barreira é o que dá valor ao teste: sem ela, a primeira thread costuma
     * terminar antes de a segunda começar, e o teste passaria <b>também sem o
     * lock</b> — virando o tipo de teste que dá confiança sem dar garantia.
     */
    private List<Resultado> aoMesmoTempo(Acao primeira, Acao segunda) {
        CyclicBarrier largada = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Resultado>> futuros = List.of(
                    pool.submit(() -> executar(largada, primeira)),
                    pool.submit(() -> executar(largada, segunda)));
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } catch (Exception e) {
            throw new IllegalStateException("falha ao rodar a corrida", e);
        }
    }

    private Resultado executar(CyclicBarrier largada, Acao acao) throws Exception {
        largada.await();
        try {
            acao.executar();
            return new Resultado(null);
        } catch (RuntimeException e) {
            return new Resultado(e);
        }
    }

    /** Uma das duas chamadas da corrida. Void porque só o desfecho interessa. */
    private interface Acao {
        void executar();
    }

    /** Como cada thread terminou: sem erro, ou com a exceção que o serviço lançou. */
    private record Resultado(RuntimeException erro) {
        boolean deuCerto() {
            return erro == null;
        }
    }

    private record Ctx(Long listId, Long ownerId, String shareCode) {
    }

    private long aprovados(Long listId) {
        return membershipRepository.countByListIdAndActiveTrueAndStatus(listId, MembershipStatus.APPROVED);
    }

    /**
     * Time com <b>uma vaga só</b>: o dono mais os membros de enchimento, que
     * entram por {@code AUTO_ACCEPT} um a um (sequencial de propósito — a corrida
     * é o que vem depois).
     */
    private Ctx timeCheioMenosUmaVaga(String world, String prefixo, JoinPolicy politica) {
        User dono = createUser(prefixo.toLowerCase() + "-dono@teste.com");
        Character personagem = createCharacter(prefixo + " Dono", world, dono);
        stubPremium(prefixo + " Dono", world);
        // A política é a do teste desde a criação: ela não é editável na API de
        // propósito, e um método "só para teste" no serviço seria produção
        // carregando peso de teste.
        ListDetailResponse detalhe = listService.createList(dono.getId(), new CreateListRequest(
                prefixo + " Team", world, creature("Demon").getId(), politica,
                personagem.getId(), null, null, null, null, null, null));
        Ctx time = new Ctx(detalhe.summary().id(), dono.getId(), detalhe.summary().shareCode());

        for (int i = 1; i <= MAX_MEMBROS - 2; i++) {
            Long enchimento = candidato(prefixo.toLowerCase() + "-enche-" + i,
                    prefixo + " Enche " + i, world);
            entrar(time, enchimento, politica);
        }
        assertThat(aprovados(time.listId)).isEqualTo(MAX_MEMBROS - 1);
        return time;
    }

    /** Faz o candidato virar membro aprovado pelo caminho real da política. */
    private void entrar(Ctx time, Long usuario, JoinPolicy politica) {
        listService.joinByShareCode(usuario, time.shareCode,
                new JoinListRequest(personagemDe(usuario)));
        if (politica == JoinPolicy.MANUAL_APPROVAL) {
            listService.approveJoinRequest(time.ownerId, time.listId,
                    pedidoDe(time, personagemDe(usuario)));
        }
    }

    /** O pedido pendente daquele personagem neste time. */
    private Long pedidoDe(Ctx time, Long characterId) {
        return membershipRepository.findAllByListIdAndActiveTrue(time.listId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.PENDING)
                .filter(m -> m.getCharacter().getId().equals(characterId))
                .findFirst().orElseThrow()
                .getId();
    }

    private final java.util.Map<Long, Long> personagemPorUsuario = new java.util.HashMap<>();

    private Long candidato(String email, String nomeDoPersonagem, String world) {
        User usuario = createUser(email + "@teste.com");
        Character personagem = createCharacter(nomeDoPersonagem, world, usuario);
        stubPremium(nomeDoPersonagem, world);
        personagemPorUsuario.put(usuario.getId(), personagem.getId());
        return usuario.getId();
    }

    private Long personagemDe(Long userId) {
        return personagemPorUsuario.get(userId);
    }

    /** Candidato com pedido já pendente, pronto para o dono aprovar. */
    private Long pedidoPendente(Ctx time, String email, String nomeDoPersonagem, String world) {
        Long usuario = candidato(email, nomeDoPersonagem, world);
        listService.joinByShareCode(usuario, time.shareCode, new JoinListRequest(personagemDe(usuario)));
        return pedidoDe(time, personagemDe(usuario));
    }
}
