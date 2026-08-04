package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MyTeamsScope;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.service.HuntingListService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Meus times" é paginado e recortado por aba <b>no servidor</b> (item P12).
 *
 * <p><b>O que era medido antes:</b> numa conta com 12 times, a resposta trazia os 12 e
 * custava 28 consultas — e <b>9 dos 12 eram histórico</b>, que a tela nem mostra ao abrir
 * (ela abre na aba de ativos). O limite do plano free é de times <b>ativos</b>, então
 * criar-arquivar-criar acumula histórico sem teto: a resposta crescia com o tempo de uso, e
 * quem sentiria primeiro é o usuário mais fiel.</p>
 *
 * <p>⚠️ Metade destes testes existe para o que <b>não</b> pode mudar junto: a união
 * (dono <b>ou</b> membro aprovado) sem duplicar, e o total honesto. O total é o ponto mais
 * frágil — a tela usa ele para o contador da aba e para o aviso do limite do plano, então
 * um total que conte "o que veio nesta página" faz o aviso mentir sobre quantos times ativos
 * a pessoa tem.</p>
 */
class MyTeamsPaginationIntegrationTest extends TeamIntegrationTestBase {

    @DynamicPropertySource
    static void semTetoDeCriacao(DynamicPropertyRegistry registry) {
        // O teste cria mais times por hora que um humano; o limite não é o assunto aqui.
        registry.add("app.rate-limit.team-creation-per-hour", () -> 200);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    }

    @Autowired HuntingListService listService;
    @Autowired HuntingListRepository listRepository;
    @Autowired EntityManagerFactory emf;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final PageRequest PRIMEIRA = PageRequest.of(0, 20);

    @Test
    void aAbaDeAtivosNaoPagaPeloHistorico() {
        User dono = createUser("mine-ativos@teste.com");
        criarTime(dono, "MineAtivo A", TeamStatus.ACTIVE);
        criarTime(dono, "MineAtivo B", TeamStatus.ARCHIVED);
        criarTime(dono, "MineAtivo C", TeamStatus.CLOSED);
        criarTime(dono, "MineAtivo D", TeamStatus.COMPLETED);

        Page<ListSummaryResponse> ativos = listService.listMyLists(dono.getId(), MyTeamsScope.ACTIVE, PRIMEIRA);

        assertThat(ativos.getContent()).extracting(ListSummaryResponse::status)
                .containsExactly(TeamStatus.ACTIVE);
        assertThat(ativos.getTotalElements()).isEqualTo(1);
    }

    @Test
    void oHistoricoEhTudoQueNaoEstaAtivo() {
        // ⚠️ Definido por exclusão de propósito: um TeamStatus novo cai aqui sozinho. Com
        // lista fechada de status ele ficaria invisível nas **duas** abas, e ninguém
        // descobriria — a soma das duas nunca é conferida contra o total.
        User dono = createUser("mine-hist@teste.com");
        criarTime(dono, "MineHist A", TeamStatus.ACTIVE);
        criarTime(dono, "MineHist B", TeamStatus.ARCHIVED);
        criarTime(dono, "MineHist C", TeamStatus.CLOSED);
        criarTime(dono, "MineHist D", TeamStatus.COMPLETED);

        Page<ListSummaryResponse> historico =
                listService.listMyLists(dono.getId(), MyTeamsScope.HISTORY, PRIMEIRA);

        assertThat(historico.getContent()).extracting(ListSummaryResponse::status)
                .containsExactlyInAnyOrder(TeamStatus.ARCHIVED, TeamStatus.CLOSED, TeamStatus.COMPLETED);
        assertThat(historico.getTotalElements()).isEqualTo(3);
    }

    @Test
    void oTotalEhDaAbaInteiraENaoDaPaginaNemDoUsuario() {
        // O teste do contador: a tela mostra `totalElements` no rótulo da aba e no aviso do
        // plano free. Se ele contasse a página, o aviso diria "1/3" para quem tem 3 ativos.
        //
        // ⚠️ **Duas condições que este teste precisa criar, e cada uma pega uma mutação
        // diferente** — as duas sobreviveram na primeira rodada por faltarem aqui:
        //
        //   1. **mais de uma página** (`size = 1`): o Spring Data **pula a consulta de
        //      contagem** quando a primeira página já contém tudo, e usa o tamanho da lista.
        //      Num teste de página única, erro no `countQuery` é invisível;
        //   2. **histórico junto**: sem ele, "contar a aba" e "contar todos os times do
        //      usuário" dão o mesmo número, e tirar o filtro de status do `countQuery`
        //      também passa batido.
        // ⚠️ O histórico primeiro: o limite do plano free é de **3 ativos**, então criar os
        // arquivados depois dos três ativos é recusado. É a mesma ordem que um usuário real
        // percorre (criar, deixar expirar, criar de novo).
        User dono = createUser("mine-total@teste.com");
        criarTime(dono, "MineTotal D", TeamStatus.ARCHIVED);
        criarTime(dono, "MineTotal E", TeamStatus.CLOSED);
        criarTime(dono, "MineTotal A", TeamStatus.ACTIVE);
        criarTime(dono, "MineTotal B", TeamStatus.ACTIVE);
        criarTime(dono, "MineTotal C", TeamStatus.ACTIVE);

        Page<ListSummaryResponse> primeira =
                listService.listMyLists(dono.getId(), MyTeamsScope.ACTIVE, PageRequest.of(0, 1));

        assertThat(primeira.getContent()).hasSize(1);
        assertThat(primeira.getTotalElements())
                .as("os 3 ativos, e não os 5 times do usuário")
                .isEqualTo(3);
        assertThat(primeira.getTotalPages()).isEqualTo(3);
    }

    @Test
    void paginarNaoRepeteNemEsconde() {
        // O empate é **forçado no banco**: o `created_at` vem de `Instant.now()` com
        // precisão de microssegundo, então três times criados em sequência não empatam.
        //
        // ⚠️ **Este teste NÃO pega a falta do `id desc`**, e isso está medido: mesmo com os
        // três empatados, o plano é um `Sort` sobre `Seq Scan` e o Postgres devolve a mesma
        // ordem em toda execução, então apagar o desempate passa na suíte. O `id desc`
        // continua no `order by` porque a ordem de um `Sort` sem chave total **não é
        // garantida** — muda com plano paralelo, índice novo ou ordenação em disco. É defesa
        // contra o que o planejador tem liberdade de mudar, não contra um caso reproduzível
        // hoje; a mutação está registrada como inalcançável em
        // new-features/meus-times-em-duas-abas.md §5. O que este teste garante de fato é o
        // que importa para a tela: paginar não repete nem esconde item.
        User dono = createUser("mine-ordem@teste.com");
        criarTime(dono, "MineOrdem A", TeamStatus.ACTIVE);
        criarTime(dono, "MineOrdem B", TeamStatus.ACTIVE);
        criarTime(dono, "MineOrdem C", TeamStatus.ACTIVE);
        jdbcTemplate.update("update hunting_lists set created_at = ? where owner_id = ?",
                java.sql.Timestamp.from(java.time.Instant.now()), dono.getId());

        List<Long> vistos = new java.util.ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            listService.listMyLists(dono.getId(), MyTeamsScope.ACTIVE, PageRequest.of(pagina, 1))
                    .forEach(s -> vistos.add(s.id()));
        }

        assertThat(vistos).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void quemEDonoEMembroDoMesmoTimeVeOTimeUmaVezSo() {
        // A de-duplicação que a união em memória fazia. O `exists` do JPQL a preserva; um
        // `join` nas participações traria o time duas vezes para o dono que também é membro.
        User dono = createUser("mine-dedup@teste.com");
        Long listId = criarTime(dono, "MineDedup A", TeamStatus.ACTIVE);

        Page<ListSummaryResponse> ativos = listService.listMyLists(dono.getId(), MyTeamsScope.ACTIVE, PRIMEIRA);

        assertThat(ativos.getContent()).extracting(ListSummaryResponse::id).containsExactly(listId);
        assertThat(ativos.getTotalElements()).isEqualTo(1);
    }

    @Test
    void membroAprovadoDeOutroDonoTambemVeOTime() {
        User dono = createUser("mine-uniao-dono@teste.com");
        Long listId = criarTime(dono, "MineUniao", TeamStatus.ACTIVE);
        String shareCode = listRepository.findById(listId).orElseThrow().getShareCode();
        User membro = createUser("mine-uniao-membro@teste.com");
        Character dele = createCharacter("Mine Uniao Membro", "MineUniaoWorld", membro);
        stubPremium("Mine Uniao Membro", "MineUniaoWorld", 500, "Elite Knight");
        listService.joinByShareCode(membro.getId(), shareCode, new JoinListRequest(dele.getId()));

        Page<ListSummaryResponse> ativos =
                listService.listMyLists(membro.getId(), MyTeamsScope.ACTIVE, PRIMEIRA);

        assertThat(ativos.getContent()).extracting(ListSummaryResponse::id).containsExactly(listId);
    }

    @Test
    void pedidoPendenteNaoContaComoMeuTime() {
        // ⚠️ Regra de produto, e uma mutação sobreviveu até este teste existir: tirar o
        // `status = APPROVED` do `exists` faz um **pedido pendente** aparecer em "meus
        // times". Quem pediu acharia que já está dentro — e é exatamente o limbo que o P4
        // resolveu pondo o pedido numa aba própria.
        User dono = createUser("mine-pend-dono@teste.com");
        Long listId = criarTime(dono, "MinePend", TeamStatus.ACTIVE, JoinPolicy.MANUAL_APPROVAL);
        String shareCode = listRepository.findById(listId).orElseThrow().getShareCode();
        User candidato = createUser("mine-pend-cand@teste.com");
        Character dele = createCharacter("Mine Pend Cand", "MinePendWorld", candidato);
        stubPremium("Mine Pend Cand", "MinePendWorld", 500, "Elite Knight");
        listService.joinByShareCode(candidato.getId(), shareCode, new JoinListRequest(dele.getId()));

        Page<ListSummaryResponse> ativos =
                listService.listMyLists(candidato.getId(), MyTeamsScope.ACTIVE, PRIMEIRA);

        assertThat(ativos.getContent()).isEmpty();
        assertThat(ativos.getTotalElements()).isZero();
    }

    @Test
    void oCustoDaAbaDeAtivosNaoCresceComOHistorico() {
        // ⚠️ O teste que impede a volta da união em memória: paginar depois de carregar tudo
        // devolveria a mesma resposta e continuaria custando o histórico inteiro. Aqui o
        // número de consultas é comparado **antes e depois** de o histórico dobrar.
        User dono = createUser("mine-custo@teste.com");
        criarTime(dono, "MineCusto Ativo", TeamStatus.ACTIVE);
        for (int i = 1; i <= 8; i++) {
            criarTime(dono, "MineCusto Velho " + i, TeamStatus.ARCHIVED);
        }
        long comOitoDeHistorico = consultasParaListarAtivos(dono.getId());

        for (int i = 9; i <= 16; i++) {
            criarTime(dono, "MineCusto Velho " + i, TeamStatus.ARCHIVED);
        }
        long comDezesseis = consultasParaListarAtivos(dono.getId());

        assertThat(comDezesseis)
                .as("dobrar o histórico não pode custar consulta nenhuma na aba de ativos")
                .isEqualTo(comOitoDeHistorico);
    }

    // ----- Helpers -----

    private long consultasParaListarAtivos(Long userId) {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        listService.listMyLists(userId, MyTeamsScope.ACTIVE, PRIMEIRA);
        return stats.getPrepareStatementCount();
    }

    private Long criarTime(User dono, String nome, TeamStatus status) {
        return criarTime(dono, nome, status, JoinPolicy.AUTO_ACCEPT);
    }

    /** Cria um time do dono e o deixa no status pedido (o job de expiração faz o mesmo). */
    private Long criarTime(User dono, String nome, TeamStatus status, JoinPolicy politica) {
        String world = nome.split(" ")[0] + "World";
        Character personagem = createCharacter(nome + " Char", world, dono);
        stubPremium(nome + " Char", world, 500, "Elder Druid");
        ListDetailResponse time = listService.createList(dono.getId(), new CreateListRequest(
                nome, world, creature("Demon").getId(), politica,
                personagem.getId(), null, null, null, null, null, null));
        if (status != TeamStatus.ACTIVE) {
            HuntingList entidade = listRepository.findById(time.summary().id()).orElseThrow();
            entidade.setStatus(status);
            listRepository.save(entidade);
        }
        return time.summary().id();
    }
}
