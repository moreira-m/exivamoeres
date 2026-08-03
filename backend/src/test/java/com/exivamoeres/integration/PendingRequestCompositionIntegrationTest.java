package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.JoinRequestIssue;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.MyJoinRequestResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.dto.list.UpdateSlotsRequest;
import com.exivamoeres.dto.notification.NotificationResponse;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pedido pendente que a **composição** deixou de aceitar (item P21).
 *
 * O [P18] fechou este limbo para o <b>level mínimo</b>: se o dono sobe o requisito
 * acima do personagem, quem pediu é avisado. A composição por vocação ([P3]) abriu um
 * segundo caminho para o mesmo limbo e ninguém avisava: o dono troca
 * {@code [KNIGHT, DRUID]} por {@code [DRUID, DRUID]}, o pedido do Knight virou
 * inaprovável, e quem pediu segue esperando — a recusa só aparece quando o dono tenta
 * aprovar, numa mensagem que só o dono lê.
 *
 * <p>Como na família do P18, <b>metade destes testes existe para provar o que NÃO
 * notifica</b>: reordenar sem tirar a vaga, remover a composição, quem já não cabia, e
 * o membro aprovado (que nem chega aqui — a operação é recusada antes).</p>
 */
class PendingRequestCompositionIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired NotificationService notificationService;

    @Test
    void tirarAVagaDaVocacaoAvisaQuemTemPedidoPendente() {
        // Pedido de um Knight num time [KNIGHT, DRUID].
        Ctx ctx = timeComPedidoDeKnight("CompoWorld", "Compo", Vocation.KNIGHT, Vocation.DRUID);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        assertThat(tipos(ctx.requesterId))
                .contains(NotificationType.JOIN_REQUEST_COMPOSITION_MISMATCH);
    }

    @Test
    void aAbaMeusPedidosExplicaOMotivoComAVocacao() {
        Ctx ctx = timeComPedidoDeKnight("CompoIssueWorld", "CompoIssue", Vocation.KNIGHT, Vocation.DRUID);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        MyJoinRequestResponse pedido = pedidoDe(ctx);
        // Notificação empurra, tela explica: o código diz o motivo e a vocação diz
        // **qual** ficou sem vaga — é o que torna "use outro personagem" acionável.
        assertThat(pedido.issue()).isEqualTo(JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION);
        assertThat(pedido.characterVocation()).isEqualTo(Vocation.KNIGHT);
    }

    @Test
    void reordenarSemTirarAVagaNaoAvisa() {
        // ⚠️ Prefixo "CompoReorder", não "Reorder": o TeamSlotIntegrationTest também
        // monta um "Reorder Dono", e o índice único de nome de personagem é do
        // **banco compartilhado** por toda a suíte. Com os dois iguais, quem rodasse
        // por último estourava — e qual dos dois é "o último" muda quando uma classe
        // nova entra na pasta (foi assim que apareceu).
        Ctx ctx = timeComPedidoDeKnight("CompoReorderWorld", "CompoReorder", Vocation.KNIGHT, Vocation.DRUID);
        long antes = notificationService.countUnread(ctx.requesterId);

        // A vaga de Knight mudou de posição, mas continua existindo.
        trocarComposicao(ctx, Vocation.DRUID, Vocation.KNIGHT);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(pedidoDe(ctx).issue()).isNull();
    }

    @Test
    void vagaLivreNaComposicaoNovaNaoAvisa() {
        Ctx ctx = timeComPedidoDeKnight("FreeSlotWorld", "FreeSlot", Vocation.KNIGHT, Vocation.DRUID);
        long antes = notificationService.countUnread(ctx.requesterId);

        // `null` é vaga livre: aceita qualquer vocação, inclusive Knight.
        trocarComposicao(ctx, Vocation.DRUID, null);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(pedidoDe(ctx).issue()).isNull();
    }

    @Test
    void removerAComposicaoNaoAvisa() {
        Ctx ctx = timeComPedidoDeKnight("RemoveCompoWorld", "RemoveCompo", Vocation.KNIGHT, Vocation.DRUID);
        long antes = notificationService.countUnread(ctx.requesterId);

        // Time sem composição aceita qualquer vocação: ninguém passou a ficar de fora.
        listService.replaceSlots(ctx.ownerId, ctx.listId, new UpdateSlotsRequest(List.of()));

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(pedidoDe(ctx).issue()).isNull();
    }

    @Test
    void naoAvisaDeNovoQuemJaEstavaSemVaga() {
        Ctx ctx = timeComPedidoDeKnight("AgainWorld", "Again", Vocation.KNIGHT, Vocation.DRUID);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID); // aqui sim: perdeu a vaga

        trocarComposicao(ctx, Vocation.DRUID, Vocation.PALADIN); // já estava sem vaga

        // Reconfigurar de novo não é notícia nova — o recorte de transição do P18.
        assertThat(tipos(ctx.requesterId))
                .filteredOn(tipo -> tipo == NotificationType.JOIN_REQUEST_COMPOSITION_MISMATCH)
                .hasSize(1);
    }

    @Test
    void oDonoNaoRecebeAvisoDaPropriaReconfiguracao() {
        Ctx ctx = timeComPedidoDeKnight("OwnerCompoWorld", "OwnerCompo", Vocation.KNIGHT, Vocation.DRUID);
        long antes = notificationService.countUnread(ctx.ownerId);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        assertThat(notificationService.countUnread(ctx.ownerId)).isEqualTo(antes);
    }

    @Test
    void pedidoJaRecusadoNaoAvisa() {
        Ctx ctx = timeComPedidoDeKnight("RejCompoWorld", "RejCompo", Vocation.KNIGHT, Vocation.DRUID);
        listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);
        long antes = notificationService.countUnread(ctx.requesterId);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void oAvisoApontaOTimeParaOLinkDaTela() {
        Ctx ctx = timeComPedidoDeKnight("LinkCompoWorld", "LinkCompo", Vocation.KNIGHT, Vocation.DRUID);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        assertThat(notificacoes(ctx.requesterId))
                .filteredOn(n -> n.type() == NotificationType.JOIN_REQUEST_COMPOSITION_MISMATCH)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.listId()).isEqualTo(ctx.listId);
                    assertThat(n.listName()).isEqualTo("LinkCompo Team");
                });
    }

    @Test
    void motivoDefinitivoGanhaDoConsertavel() {
        // O personagem perde a vaga **e** fica abaixo do level mínimo ao mesmo tempo.
        Ctx ctx = timeComPedidoDeKnight("PrecedenceWorld", "Precedence", Vocation.KNIGHT, Vocation.DRUID);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);
        // Level do candidato abaixo do que o time vai exigir. O mínimo tem que caber no
        // **dono** também (level 500): outra regra do projeto, e ela recusaria 900.
        Character knight = characterRepository.findById(ctx.requesterCharacterId).orElseThrow();
        knight.setLevel(200);
        characterRepository.save(knight);
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(ctx.teamName, 400, null, null, null, null));

        // Subir de level não resolveria nada: sem vaga para Knight, a aprovação nunca
        // vem. Mostrar o motivo consertável aqui manda a pessoa jogar por nada.
        assertThat(pedidoDe(ctx).issue()).isEqualTo(JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION);
    }

    @Test
    void semComposicaoNaoHaMotivoDeVocacao() {
        // Time sem composição nenhuma: qualquer vocação cabe, sempre.
        Ctx ctx = timeComPedidoDeKnight("NoCompoWorld", "NoCompo");

        assertThat(pedidoDe(ctx).issue()).isNull();
    }

    // ------------------------------------------------------------------------
    // P28 — trocar de motivo, continuando de fora.
    //
    // A regra de transição avisa quem **atravessa** a fronteira do "cabe". Ficar do lado
    // de fora com outro motivo não avisava — e há um caso em que isso perde informação
    // que muda a ação da pessoa: sair de "abaixo do level" (**consertável**: jogue) para
    // "sem vaga para a vocação" (**definitivo**: use outro personagem).
    //
    // ⚠️ Só nesse sentido. Os outros dois não avisam, e isso é o assunto de metade dos
    // testes daqui: cada aviso a mais nesta família aproxima o ponto em que a pessoa
    // silencia todos.
    // ------------------------------------------------------------------------

    @Test
    void trocarLevelPorComposicaoAvisaQueOMotivoVirouDefinitivo() {
        Ctx ctx = timeComPedidoDeKnightLevel150("P28aWorld", "P28a", Vocation.KNIGHT, Vocation.DRUID);
        subirLevelMinimo(ctx, 300);
        // Agora ele está fora por level (consertável) — e foi avisado disso (P18).
        assertThat(pedidoDe(ctx).issue()).isEqualTo(JoinRequestIssue.BELOW_MINIMUM_LEVEL);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        // O motivo virou definitivo: subir de level não resolve mais nada.
        assertThat(pedidoDe(ctx).issue()).isEqualTo(JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION);
        assertThat(tipos(ctx.requesterId))
                .as("um aviso de cada, na ordem em que os motivos mudaram")
                .containsExactly(NotificationType.JOIN_REQUEST_COMPOSITION_MISMATCH,
                        NotificationType.JOIN_REQUEST_AT_RISK);
    }

    @Test
    void trocarComposicaoPorLevelNaoAvisa() {
        // O sentido inverso: de definitivo para consertável. É boa notícia **parcial** — a
        // pessoa continua sem poder ser aprovada —, e a aba "meus pedidos" já mostra o
        // motivo novo para quem abrir a tela.
        Ctx ctx = timeComPedidoDeKnightLevel150("P28bWorld", "P28b", Vocation.KNIGHT, Vocation.DRUID);
        subirLevelMinimo(ctx, 300);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);
        int antes = tipos(ctx.requesterId).size();

        // A vaga de Knight volta: o motivo cai para "abaixo do level".
        trocarComposicao(ctx, Vocation.KNIGHT, Vocation.DRUID);

        assertThat(pedidoDe(ctx).issue()).isEqualTo(JoinRequestIssue.BELOW_MINIMUM_LEVEL);
        assertThat(tipos(ctx.requesterId)).hasSize(antes);
    }

    @Test
    void reconfigurarDeNovoComOMesmoMotivoDefinitivoNaoAvisaDeNovo() {
        // O recorte continua sendo **transição**: quem já estava fora por composição não
        // recebe o mesmo aviso a cada reconfiguração.
        Ctx ctx = timeComPedidoDeKnightLevel150("P28cWorld", "P28c", Vocation.KNIGHT, Vocation.DRUID);
        subirLevelMinimo(ctx, 300);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);
        int antes = tipos(ctx.requesterId).size();

        trocarComposicao(ctx, Vocation.DRUID, Vocation.PALADIN);

        assertThat(pedidoDe(ctx).issue()).isEqualTo(JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION);
        assertThat(tipos(ctx.requesterId)).hasSize(antes);
    }

    @Test
    void quemCabiaEDeixouDeCaberContinuaRecebendoUmAvisoSo() {
        // Guarda-corpo do caminho antigo: o ramo novo do P28 não pode fazer quem
        // atravessou a fronteira receber dois avisos.
        Ctx ctx = timeComPedidoDeKnightLevel150("P28dWorld", "P28d", Vocation.KNIGHT, Vocation.DRUID);

        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        assertThat(tipos(ctx.requesterId))
                .containsExactly(NotificationType.JOIN_REQUEST_COMPOSITION_MISMATCH);
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long requesterId, Long requesterCharacterId,
                       Long membershipId, String shareCode, String teamName) {
    }

    /**
     * Time com a composição dada e um pedido <b>pendente</b> de um Knight.
     *
     * O dono é Elder Druid: assim ele cabe em `[KNIGHT, DRUID]` e nas variações que os
     * testes montam (a composição nova precisa caber em quem já está no time, então um
     * dono Knight recusaria metade das reconfigurações antes de chegar ao que se quer
     * medir).
     */
    private Ctx timeComPedidoDeKnight(String world, String prefixo, Vocation... composicao) {
        User dono = createUser(prefixo.toLowerCase() + "-compo-dono@teste.com");
        Character donoChar = createCharacter(prefixo + " Dono", world, dono);
        stubPremium(prefixo + " Dono", world, 500, "Elder Druid");
        ListDetailResponse time = listService.createList(dono.getId(), new CreateListRequest(
                prefixo + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                donoChar.getId(), null, null, null, null, null,
                composicao.length == 0 ? null : Arrays.asList(composicao)));

        User quemPediu = createUser(prefixo.toLowerCase() + "-compo-pediu@teste.com");
        Character knight = createCharacter(prefixo + " Knight", world, quemPediu);
        stubPremium(prefixo + " Knight", world, 500, "Elite Knight");
        ListDetailResponse comPedido = listService.joinByShareCode(
                quemPediu.getId(), time.summary().shareCode(), new JoinListRequest(knight.getId()));
        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(quemPediu.getId()))
                .findFirst().orElseThrow().id();

        return new Ctx(time.summary().id(), dono.getId(), quemPediu.getId(), knight.getId(),
                membershipId, time.summary().shareCode(), time.summary().name());
    }

    /**
     * Como o {@link #timeComPedidoDeKnight}, mas o Knight é <b>level 150</b> — abaixo do
     * que os testes do P28 vão exigir depois.
     *
     * ⚠️ O time nasce **sem** level mínimo de propósito: com o mínimo já acima de 150, o
     * pedido seria recusado no `join` e não haveria pendente para observar. O caminho real
     * é o mesmo: primeiro o dono sobe o requisito (P18), depois mexe na composição.
     */
    private Ctx timeComPedidoDeKnightLevel150(String world, String prefixo, Vocation... composicao) {
        User dono = createUser(prefixo.toLowerCase() + "-p28-dono@teste.com");
        Character donoChar = createCharacter(prefixo + " Dono", world, dono);
        stubPremium(prefixo + " Dono", world, 500, "Elder Druid");
        ListDetailResponse time = listService.createList(dono.getId(), new CreateListRequest(
                prefixo + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                donoChar.getId(), null, null, null, null, null,
                composicao.length == 0 ? null : Arrays.asList(composicao)));

        User quemPediu = createUser(prefixo.toLowerCase() + "-p28-pediu@teste.com");
        Character knight = createCharacter(prefixo + " Knight", world, quemPediu);
        stubPremium(prefixo + " Knight", world, 150, "Elite Knight");
        ListDetailResponse comPedido = listService.joinByShareCode(
                quemPediu.getId(), time.summary().shareCode(), new JoinListRequest(knight.getId()));
        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(quemPediu.getId()))
                .findFirst().orElseThrow().id();

        return new Ctx(time.summary().id(), dono.getId(), quemPediu.getId(), knight.getId(),
                membershipId, time.summary().shareCode(), time.summary().name());
    }

    private void subirLevelMinimo(Ctx ctx, int minimo) {
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(ctx.teamName, minimo, null, null, null, null));
    }

    /** Reconfigura a composição, como o `PUT /api/lists/{id}/slots` da tela do dono. */
    private void trocarComposicao(Ctx ctx, Vocation... vagas) {
        listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(Arrays.asList(vagas)));
    }

    /** O pedido como a aba "meus pedidos" o entrega para quem pediu. */
    private MyJoinRequestResponse pedidoDe(Ctx ctx) {
        return listService.listMyJoinRequests(ctx.requesterId).stream()
                .filter(p -> p.id().equals(ctx.membershipId))
                .findFirst().orElseThrow();
    }

    private List<NotificationResponse> notificacoes(Long userId) {
        return notificationService.list(userId, PageRequest.of(0, 20)).getContent();
    }

    private List<NotificationType> tipos(Long userId) {
        return notificacoes(userId).stream().map(NotificationResponse::type).toList();
    }
}
