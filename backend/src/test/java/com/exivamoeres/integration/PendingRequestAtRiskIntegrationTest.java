package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.dto.notification.NotificationResponse;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avisar quem tem pedido **pendente** que o requisito do time mudou (item P18).
 *
 * O [P16] avisa os membros aprovados; quem pediu e ainda espera não recebia nada —
 * e é quem mais precisa, porque um level mínimo maior torna o pedido inaprovável.
 *
 * Como sempre nesta família de testes, **metade existe para provar o que NÃO
 * notifica**: mudança que não afeta o pedido, quem já estava de fora antes, horário,
 * level desconhecido e pedido já decidido.
 */
class PendingRequestAtRiskIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired NotificationService notificationService;

    @Test
    void subirOLevelMinimoAcimaDoPedidoAvisaOSolicitante() {
        // Personagem level 150 num time que exigia 100.
        Ctx ctx = timeComPedido("AtRiskWorld", "AtRisk", 100, 150);

        subirMinimoPara(ctx, 300);

        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_AT_RISK);
    }

    @Test
    void mudarOLevelMinimoSemAfetarOPedidoNaoAvisa() {
        // Personagem level 250: o mínimo sobe para 200 e ele continua cabendo.
        Ctx ctx = timeComPedido("SafeChangeWorld", "Safe", 100, 250);
        long antes = notificationService.countUnread(ctx.requesterId);

        subirMinimoPara(ctx, 200);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
        assertThat(tipos(ctx.requesterId)).doesNotContain(NotificationType.JOIN_REQUEST_AT_RISK);
    }

    @Test
    void naoAvisaDeNovoQuemJaEstavaDeFora() {
        Ctx ctx = timeComPedido("AlreadyOutWorld", "Already", 100, 150);
        subirMinimoPara(ctx, 300); // aqui sim: passou a ficar de fora

        subirMinimoPara(ctx, 400); // já estava de fora — não é notícia nova

        assertThat(tipos(ctx.requesterId))
                .filteredOn(tipo -> tipo == NotificationType.JOIN_REQUEST_AT_RISK)
                .hasSize(1);
    }

    @Test
    void baixarOLevelMinimoAvisaQueOPedidoVoltouACaber() {
        // O pedido só existe se o personagem cabia no requisito na hora de pedir,
        // então "já em risco" se constrói em dois passos: pedir, depois subir.
        Ctx ctx = timeComPedido("LowerWorld", "Lower", 100, 150);
        subirMinimoPara(ctx, 300);

        subirMinimoPara(ctx, 100); // requisito afrouxou de novo

        // ⚠️ Este teste dizia o contrário até 30/07/2026 ("boa notícia não é avisada
        // hoje") — era a ausência do P19 registrada como comportamento. O P19 fechou a
        // outra ponta: quem desistiu mentalmente do pedido precisa saber que voltou a
        // dar. A cobertura de "não avisa" continua, com os casos em que a pessoa
        // **continua** sem caber (ver PendingRequestFitsAgainIntegrationTest).
        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void removerOLevelMinimoAvisaQueOPedidoVoltouACaber() {
        Ctx ctx = timeComPedido("NoMinWorld", "NoMin", 100, 150);
        subirMinimoPara(ctx, 300);

        // Requisito removido: todo pedido volta a caber pelo critério de level.
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest("NoMin Team", null, null, null, null, null));

        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_FITS_AGAIN);
    }

    @Test
    void mudarSoOHorarioNaoAvisaQuemEstaPendente() {
        Ctx ctx = timeComPedido("SchedPendingWorld", "SchedP", 100, 150);
        long antes = notificationService.countUnread(ctx.requesterId);

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(ctx.teamName, 100, null, null, "Sáb 3h da manhã", null));

        // Quem ainda não entrou provavelmente não se organizou em volta do horário.
        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void levelDesconhecidoNaoAvisa() {
        Ctx ctx = timeComPedido("UnknownLevelWorld", "Unknown", 100, 150);
        // Personagem sem level sincronizado: não há violação a provar.
        Character chr = characterRepository.findById(ctx.requesterCharacterId).orElseThrow();
        chr.setLevel(null);
        characterRepository.save(chr);
        long antes = notificationService.countUnread(ctx.requesterId);

        subirMinimoPara(ctx, 300);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void pedidoJaRecusadoNaoAvisa() {
        Ctx ctx = timeComPedido("RejectedPendingWorld", "RejP", 100, 150);
        listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);
        long antes = notificationService.countUnread(ctx.requesterId);

        subirMinimoPara(ctx, 300);

        assertThat(notificationService.countUnread(ctx.requesterId)).isEqualTo(antes);
    }

    @Test
    void oDonoNaoRecebeAviso() {
        Ctx ctx = timeComPedido("OwnerAtRiskWorld", "OwnerR", 100, 150);
        long antes = notificationService.countUnread(ctx.ownerId);

        subirMinimoPara(ctx, 300);

        assertThat(notificationService.countUnread(ctx.ownerId)).isEqualTo(antes);
    }

    @Test
    void membroAprovadoContinuaRecebendoOAvisoDeLevelMinimo() {
        // ⚠️ Prefixo/nome exclusivos: nome de personagem é único GLOBAL (índice em
        // lower(name)) e a base é compartilhada entre as classes de teste — "Both"
        // já é usado pelo TeamUpdateNotificationIntegrationTest.
        String world = "TwoRolesWorld";
        Ctx ctx = timeComPedido(world, "TwoRoles", 100, 150);
        // Um membro aprovado no mesmo time, além do pedido pendente.
        User membro = createUser("dois-papeis-membro@teste.com");
        Character membroChar = createCharacter("TwoRoles Membro", world, membro);
        stubPremium("TwoRoles Membro", world, 400, "Elder Druid");
        ListDetailResponse comPedido = listService.joinByShareCode(
                membro.getId(), ctx.shareCode, new JoinListRequest(membroChar.getId()));
        Long membershipDoMembro = comPedido.members().stream()
                .filter(m -> m.userId().equals(membro.getId()))
                .findFirst().orElseThrow().id();
        listService.approveJoinRequest(ctx.ownerId, ctx.listId, membershipDoMembro);

        subirMinimoPara(ctx, 300);

        // Cada um recebe o aviso escrito para o seu papel — o do P16 e o do P18.
        assertThat(tipos(membro.getId())).contains(NotificationType.TEAM_MINIMUM_LEVEL_CHANGED);
        assertThat(tipos(membro.getId())).doesNotContain(NotificationType.JOIN_REQUEST_AT_RISK);
        assertThat(tipos(ctx.requesterId)).contains(NotificationType.JOIN_REQUEST_AT_RISK);
        assertThat(tipos(ctx.requesterId)).doesNotContain(NotificationType.TEAM_MINIMUM_LEVEL_CHANGED);
    }

    @Test
    void oAvisoApontaOTimeParaOLinkDaTela() {
        Ctx ctx = timeComPedido("LinkAtRiskWorld", "LinkR", 100, 150);

        subirMinimoPara(ctx, 300);

        assertThat(notificacoes(ctx.requesterId))
                .filteredOn(n -> n.type() == NotificationType.JOIN_REQUEST_AT_RISK)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.listId()).isEqualTo(ctx.listId);
                    assertThat(n.listName()).isEqualTo("LinkR Team");
                });
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long requesterId, Long requesterCharacterId,
                       Long membershipId, String shareCode, String teamName) {
    }

    /** Time MANUAL_APPROVAL com um pedido PENDING de um personagem no level dado. */
    private Ctx timeComPedido(String world, String prefix, Integer minimoDoTime, int levelDoCandidato) {
        User owner = createUser(prefix.toLowerCase() + "-risco-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);
        ListDetailResponse time = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                ownerChar.getId(), minimoDoTime, null, null, null, null, null));

        User requester = createUser(prefix.toLowerCase() + "-risco-pediu@teste.com");
        Character requesterChar = createCharacter(prefix + " Pediu", world, requester);
        stubPremium(prefix + " Pediu", world, levelDoCandidato, "Royal Paladin");
        ListDetailResponse comPedido = listService.joinByShareCode(
                requester.getId(), time.summary().shareCode(), new JoinListRequest(requesterChar.getId()));
        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(requester.getId()))
                .findFirst().orElseThrow().id();

        return new Ctx(time.summary().id(), owner.getId(), requester.getId(),
                requesterChar.getId(), membershipId, time.summary().shareCode(),
                time.summary().name());
    }

    /**
     * Edita só o level mínimo — mandando o título junto, como a tela faz: o payload
     * do PATCH é o formulário inteiro, e omitir o nome faria o time voltar a se
     * chamar como a criatura-alvo (contrato do P14).
     */
    private void subirMinimoPara(Ctx ctx, int minimo) {
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(ctx.teamName, minimo, null, null, null, null));
    }

    private List<NotificationResponse> notificacoes(Long userId) {
        return notificationService.list(userId, PageRequest.of(0, 20)).getContent();
    }

    private List<NotificationType> tipos(Long userId) {
        return notificacoes(userId).stream().map(NotificationResponse::type).toList();
    }
}
