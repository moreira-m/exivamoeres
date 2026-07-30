package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
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
 * O pedido pendente que <b>voltou a caber</b> (item P19).
 *
 * A má notícia já chegava por dois caminhos — level mínimo ([P18]) e composição ([P21]).
 * A boa era silenciosa: o dono baixava o requisito ou devolvia a vaga, o pedido voltava a
 * ser aprovável, e quem pediu não ficava sabendo. A pessoa desistiu mentalmente do pedido
 * — às vezes cancelou — sem saber que agora dava.
 *
 * <p><b>O critério é o motivo sumir, não o campo melhorar</b>, e é isso que a metade
 * destes testes prende: quem recuperou a vaga mas continua abaixo do level mínimo
 * <b>não</b> é avisado, e quem está fora por world transfer nunca é — nenhuma edição do
 * time conserta isso. "Seu pedido voltou a caber" para quem segue inelegível é pior que
 * o silêncio.</p>
 */
class PendingRequestFitsAgainIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired NotificationService notificationService;

    // ---------------------------------------------------------- pelo level mínimo

    @Test
    void baixarOLevelAbaixoDoPersonagemAvisaQueVoltouACaber() {
        // Personagem 150 num time que exigia 100; o mínimo sobe para 300 (fica de fora)…
        Ctx ctx = timeComPedido("FitsLevelWorld", "FitsLevel", 100, 150);
        exigirLevel(ctx, 300);

        exigirLevel(ctx, 120); // …e volta para baixo do level dele

        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void baixarOLevelSemChegarNoPersonagemNaoAvisa() {
        Ctx ctx = timeComPedido("StillOutWorld", "StillOut", 100, 150);
        exigirLevel(ctx, 400);
        long antes = notificationService.countUnread(ctx.requesterId);

        exigirLevel(ctx, 300); // afrouxou, mas 150 continua abaixo de 300

        // Requisito menos ruim não é requisito cumprido.
        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(tipos(ctx.requesterId)).doesNotContain(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void quemNuncaEstevaDeForaNaoRecebeBoaNoticia() {
        Ctx ctx = timeComPedido("AlwaysFitWorld", "AlwaysFit", 100, 500);
        long antes = notificationService.countUnread(ctx.requesterId);

        exigirLevel(ctx, 200); // o personagem cabia antes e continua cabendo

        // Aviso para quem não mudou de situação é o começo do ruído.
        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void naoAvisaDuasVezesQuandoOTimeAfrouxaDeNovo() {
        Ctx ctx = timeComPedido("TwiceWorld", "Twice", 100, 150);
        exigirLevel(ctx, 300);
        exigirLevel(ctx, 120); // aqui sim: voltou a caber

        exigirLevel(ctx, 110); // já cabia — não é notícia nova

        assertThat(tipos(ctx.requesterId))
                .filteredOn(tipo -> tipo == NotificationType.JOIN_REQUEST_FITS_AGAIN)
                .hasSize(1);
    }

    // ---------------------------------------------------------- pela composição

    @Test
    void devolverAVagaDaVocacaoAvisaQueVoltouACaber() {
        Ctx ctx = timeComPedidoDeKnight("FitsSlotWorld", "FitsSlot", Vocation.KNIGHT, Vocation.DRUID);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID); // Knight perde a vaga

        trocarComposicao(ctx, Vocation.KNIGHT, Vocation.DRUID); // e recupera

        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void removerAComposicaoAvisaQuemEstavaSemVaga() {
        Ctx ctx = timeComPedidoDeKnight("NoCompoFitsWorld", "NoCompoFits", Vocation.KNIGHT, Vocation.DRUID);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID);

        // Time sem composição aceita qualquer vocação: todo mundo volta a caber.
        listService.replaceSlots(ctx.ownerId, ctx.listId, new UpdateSlotsRequest(List.of()));

        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void vagaDeVoltaMasLevelAindaAbaixoNaoAvisa() {
        Ctx ctx = timeComPedidoDeKnight("HalfFixWorld", "HalfFix", Vocation.KNIGHT, Vocation.DRUID);
        trocarComposicao(ctx, Vocation.DRUID, Vocation.DRUID); // perde a vaga
        rebaixarLevelDoPersonagem(ctx, 100);
        exigirLevel(ctx, 400); // e passa a estar abaixo do mínimo também
        long antes = notificationService.countUnread(ctx.requesterId);

        trocarComposicao(ctx, Vocation.KNIGHT, Vocation.DRUID); // vaga de volta, level não

        // O critério é o **motivo sumir**. Consertar metade não faz o pedido caber, e
        // dizer que voltou a caber aqui seria mentira útil para ninguém.
        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(tipos(ctx.requesterId)).doesNotContain(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void quemEstaDeForaPorWorldNaoVoltaACaberPorEdicaoDoTime() {
        Ctx ctx = timeComPedido("TransferWorld", "Transfer", 100, 150);
        exigirLevel(ctx, 300);
        // World transfer depois do pedido: nada que o dono faça no time conserta isto.
        mudarWorldDoPersonagem(ctx, "OutroWorldQualquer");
        long antes = notificationService.countUnread(ctx.requesterId);

        exigirLevel(ctx, 100); // requisito de level afrouxado

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    // ---------------------------------------------------------- destinatário

    @Test
    void pedidoJaRecusadoNaoRecebeBoaNoticia() {
        Ctx ctx = timeComPedido("RejFitsWorld", "RejFits", 100, 150);
        exigirLevel(ctx, 300);
        listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);
        long antes = notificationService.countUnread(ctx.requesterId);

        exigirLevel(ctx, 100);

        // Pedido decidido saiu da fila: reabri-lo é decisão de quem pediu, não notícia.
        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void oDonoNaoRecebeAvisoDaPropriaEdicao() {
        Ctx ctx = timeComPedido("OwnerFitsWorld", "OwnerFits", 100, 150);
        exigirLevel(ctx, 300);
        long antes = notificationService.countUnread(ctx.ownerId);

        exigirLevel(ctx, 100);

        assertThat(notificationService.countUnread(ctx.ownerId)).isEqualTo(antes);
    }

    @Test
    void oAvisoApontaOTimeParaOLinkDaTela() {
        Ctx ctx = timeComPedido("LinkFitsWorld", "LinkFits", 100, 150);
        exigirLevel(ctx, 300);

        exigirLevel(ctx, 100);

        assertThat(notificacoes(ctx.requesterId))
                .filteredOn(n -> n.type() == NotificationType.JOIN_REQUEST_FITS_AGAIN)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.listId()).isEqualTo(ctx.listId);
                    assertThat(n.listName()).isEqualTo("LinkFits Team");
                });
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long requesterId, Long requesterCharacterId,
                       Long membershipId, String shareCode, String teamName) {
    }

    /** Time MANUAL_APPROVAL, sem composição, com um pedido pendente no level dado. */
    private Ctx timeComPedido(String world, String prefixo, Integer minimoDoTime, int levelDoCandidato) {
        return time(world, prefixo, minimoDoTime, levelDoCandidato, "Royal Paladin", null);
    }

    /** Time com a composição dada e um pedido pendente de um Knight level 500. */
    private Ctx timeComPedidoDeKnight(String world, String prefixo, Vocation... composicao) {
        return time(world, prefixo, null, 500, "Elite Knight", Arrays.asList(composicao));
    }

    private Ctx time(String world, String prefixo, Integer minimoDoTime, int levelDoCandidato,
                     String vocacaoDoCandidato, List<Vocation> composicao) {
        User dono = createUser(prefixo.toLowerCase() + "-cabe-dono@teste.com");
        Character donoChar = createCharacter(prefixo + " Dono", world, dono);
        // Dono Elder Druid e level alto: cabe nas composições que os testes montam e não
        // barra os `minimumLevel` que eles pedem (o dono também precisa cumprir o mínimo).
        stubPremium(prefixo + " Dono", world, 500, "Elder Druid");
        ListDetailResponse time = listService.createList(dono.getId(), new CreateListRequest(
                prefixo + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                donoChar.getId(), minimoDoTime, null, null, null, null, composicao));

        User quemPediu = createUser(prefixo.toLowerCase() + "-cabe-pediu@teste.com");
        Character personagem = createCharacter(prefixo + " Pediu", world, quemPediu);
        stubPremium(prefixo + " Pediu", world, levelDoCandidato, vocacaoDoCandidato);
        ListDetailResponse comPedido = listService.joinByShareCode(
                quemPediu.getId(), time.summary().shareCode(), new JoinListRequest(personagem.getId()));
        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(quemPediu.getId()))
                .findFirst().orElseThrow().id();

        return new Ctx(time.summary().id(), dono.getId(), quemPediu.getId(), personagem.getId(),
                membershipId, time.summary().shareCode(), time.summary().name());
    }

    /**
     * Edita só o level mínimo — mandando o título junto, como a tela faz: o payload do
     * PATCH é o formulário inteiro, e omitir o nome faria o time voltar a se chamar como
     * a criatura-alvo (contrato do P14).
     */
    private void exigirLevel(Ctx ctx, Integer minimo) {
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(ctx.teamName, minimo, null, null, null, null));
    }

    private void trocarComposicao(Ctx ctx, Vocation... vagas) {
        listService.replaceSlots(ctx.ownerId, ctx.listId, new UpdateSlotsRequest(Arrays.asList(vagas)));
    }

    /** O level **local** (sincronizado) do personagem — é o que a detecção usa. */
    private void rebaixarLevelDoPersonagem(Ctx ctx, int level) {
        Character personagem = characterRepository.findById(ctx.requesterCharacterId).orElseThrow();
        personagem.setLevel(level);
        characterRepository.save(personagem);
    }

    private void mudarWorldDoPersonagem(Ctx ctx, String world) {
        Character personagem = characterRepository.findById(ctx.requesterCharacterId).orElseThrow();
        personagem.setWorld(world);
        characterRepository.save(personagem);
    }

    private List<NotificationResponse> notificacoes(Long userId) {
        return notificationService.list(userId, PageRequest.of(0, 20)).getContent();
    }

    private List<NotificationType> tipos(Long userId) {
        return notificacoes(userId).stream().map(NotificationResponse::type).toList();
    }
}
