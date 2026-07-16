package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.NotificationRepository;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Itens 2, 5, 6 e 7: level mínimo, expulsar/excluir (autorização) e notificações. */
class TeamManagementIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired ListMembershipRepository membershipRepository;
    @Autowired HuntingListRepository listRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationService notificationService;

    // ----- Item 2: level mínimo -----

    @Test
    void rejeitaEntradaAbaixoDoLevelMinimo() {
        User owner = createUser("lvl-owner@teste.com");
        Character ownerChar = createCharacter("Lvl Owner", "Antica", owner);
        stubPremium("Lvl Owner", "Antica", 400, "Elder Druid");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time Level 300", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), 300, null));

        User joiner = createUser("lvl-joiner@teste.com");
        Character joinerChar = createCharacter("Lvl Joiner", "Antica", joiner);
        stubPremium("Lvl Joiner", "Antica", 150, "Knight"); // abaixo de 300

        assertThatThrownBy(() -> listService.joinByShareCode(
                joiner.getId(), team.summary().shareCode(), new JoinListRequest(joinerChar.getId())))
                .hasMessageContaining("level mínimo");
    }

    @Test
    void aceitaEntradaNoLevelMinimoOuAcima() {
        User owner = createUser("lvl-owner2@teste.com");
        Character ownerChar = createCharacter("Lvl Owner Two", "Antica", owner);
        stubPremium("Lvl Owner Two", "Antica", 500, "Elder Druid");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time Level 300b", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), 300, 1_000_000L));

        User joiner = createUser("lvl-joiner2@teste.com");
        Character joinerChar = createCharacter("Lvl Joiner Two", "Antica", joiner);
        stubPremium("Lvl Joiner Two", "Antica", 300, "Royal Paladin"); // exatamente 300

        ListDetailResponse result = listService.joinByShareCode(
                joiner.getId(), team.summary().shareCode(), new JoinListRequest(joinerChar.getId()));

        assertThat(result.members()).anyMatch(m -> m.characterId().equals(joinerChar.getId()));
        assertThat(result.summary().minimumLevel()).isEqualTo(300);
        assertThat(result.summary().pricePerSlot()).isEqualTo(1_000_000L);
    }

    // ----- Item 5: expulsar -----

    @Test
    void donoExpulsaMembroEDesativaMembership() {
        Ctx ctx = teamWithOneJoiner();

        listService.kickMember(ctx.ownerId, ctx.listId, ctx.joinerMembershipId);

        var membership = membershipRepository.findById(ctx.joinerMembershipId).orElseThrow();
        assertThat(membership.isActive()).isFalse();
        // Notifica o expulso.
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(ctx.joinerId)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void naoDonoNaoPodeExpulsar() {
        Ctx ctx = teamWithOneJoiner();

        // O próprio joiner tentando expulsar → 403.
        assertThatThrownBy(() ->
                listService.kickMember(ctx.joinerId, ctx.listId, ctx.joinerMembershipId))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // ----- Item 6: excluir/encerrar -----

    @Test
    void donoEncerraTimeQueSomeDaBuscaMasFicaVisivelPorLink() {
        String world = "CloseWorld";
        User owner = createUser("close-owner@teste.com");
        Character ownerChar = createCharacter("Close Owner", world, owner);
        stubPremium("Close Owner", world);
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time a Encerrar", world, creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));
        Long listId = team.summary().id();

        listService.deleteTeam(owner.getId(), listId);

        assertThat(listRepository.findById(listId).orElseThrow().getStatus()).isEqualTo(TeamStatus.CLOSED);
        // Some da busca pública.
        var found = listService.search(world, null, null, null, PageRequest.of(0, 20)).getContent();
        assertThat(found).noneMatch(s -> s.id().equals(listId));
        // Mas continua acessível pelo link.
        assertThat(listService.getList(listId).summary().status()).isEqualTo(TeamStatus.CLOSED);
    }

    @Test
    void naoDonoNaoPodeEncerrar() {
        Ctx ctx = teamWithOneJoiner();
        assertThatThrownBy(() -> listService.deleteTeam(ctx.joinerId, ctx.listId))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void encerrarNotificaMembrosAtivos() {
        Ctx ctx = teamWithOneJoiner();
        long before = notificationRepository.countByRecipientIdAndReadFalse(ctx.joinerId);

        listService.deleteTeam(ctx.ownerId, ctx.listId);

        assertThat(notificationRepository.countByRecipientIdAndReadFalse(ctx.joinerId)).isGreaterThan(before);
    }

    // ----- Item 7: notificações (leitura e marcação) -----

    @Test
    void pedidoManualNotificaODonoEMarcaComoLida() {
        User owner = createUser("notif-owner@teste.com");
        Character ownerChar = createCharacter("Notif Owner", "Antica", owner);
        stubPremium("Notif Owner", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time Manual Notif", "Antica", creature("Demon").getId(),
                JoinPolicy.MANUAL_APPROVAL, ownerChar.getId(), null, null));

        User joiner = createUser("notif-joiner@teste.com");
        Character joinerChar = createCharacter("Notif Joiner", "Antica", joiner);
        stubPremium("Notif Joiner", "Antica");
        listService.joinByShareCode(joiner.getId(), team.summary().shareCode(),
                new JoinListRequest(joinerChar.getId()));

        var page = notificationService.list(owner.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent()).anyMatch(n -> n.type() == NotificationType.JOIN_REQUEST_RECEIVED);
        assertThat(notificationService.countUnread(owner.getId())).isGreaterThanOrEqualTo(1);

        notificationService.markAllRead(owner.getId());
        assertThat(notificationService.countUnread(owner.getId())).isZero();
    }

    // ----- Helpers -----

    private record Ctx(Long ownerId, Long joinerId, Long listId, Long joinerMembershipId) {
    }

    /** Cria um time AUTO_ACCEPT com o dono e um segundo membro aprovado. */
    private Ctx teamWithOneJoiner() {
        String uniq = String.valueOf(System.nanoTime());
        User owner = createUser("kick-owner-" + uniq + "@teste.com");
        Character ownerChar = createCharacter("Kick Owner " + uniq, "Antica", owner);
        stubPremium("Kick Owner " + uniq, "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time Kick " + uniq, "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));

        User joiner = createUser("kick-joiner-" + uniq + "@teste.com");
        Character joinerChar = createCharacter("Kick Joiner " + uniq, "Antica", joiner);
        stubPremium("Kick Joiner " + uniq, "Antica");
        listService.joinByShareCode(joiner.getId(), team.summary().shareCode(),
                new JoinListRequest(joinerChar.getId()));

        Long joinerMembershipId = membershipRepository
                .findByListIdAndCharacterId(team.summary().id(), joinerChar.getId())
                .orElseThrow().getId();
        return new Ctx(owner.getId(), joiner.getId(), team.summary().id(), joinerMembershipId);
    }
}
