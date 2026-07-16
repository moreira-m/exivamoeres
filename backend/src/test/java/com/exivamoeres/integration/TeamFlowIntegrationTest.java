package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de negócio dos times: política de entrada, world, Free Account, limite de vagas. */
class TeamFlowIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired ListMembershipRepository membershipRepository;

    @Test
    void autoAcceptEntraDiretoComoAprovado() {
        User owner = createUser("owner-auto@teste.com");
        Character ownerChar = createCharacter("Owner Auto", "Antica", owner);
        stubPremium("Owner Auto", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.AUTO_ACCEPT);

        User joiner = createUser("joiner-auto@teste.com");
        Character joinerChar = createCharacter("Joiner Auto", "Antica", joiner);
        stubPremium("Joiner Auto", "Antica");

        ListDetailResponse result = listService.joinByShareCode(
                joiner.getId(), team.summary().shareCode(), new JoinListRequest(joinerChar.getId()));

        MembershipResponse joinerMembership = result.members().stream()
                .filter(m -> m.characterId().equals(joinerChar.getId()))
                .findFirst().orElseThrow();
        assertThat(joinerMembership.status()).isEqualTo(MembershipStatus.APPROVED);
    }

    @Test
    void manualApprovalFicaPendenteAteAprovar() {
        User owner = createUser("owner-manual@teste.com");
        Character ownerChar = createCharacter("Owner Manual", "Antica", owner);
        stubPremium("Owner Manual", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.MANUAL_APPROVAL);

        User joiner = createUser("joiner-manual@teste.com");
        Character joinerChar = createCharacter("Joiner Manual", "Antica", joiner);
        stubPremium("Joiner Manual", "Antica");

        listService.joinByShareCode(joiner.getId(), team.summary().shareCode(),
                new JoinListRequest(joinerChar.getId()));

        var pending = listService.listPendingRequests(owner.getId(), team.summary().id());
        assertThat(pending).hasSize(1);

        listService.approveJoinRequest(owner.getId(), team.summary().id(), pending.get(0).id());

        var membership = membershipRepository.findById(pending.get(0).id()).orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.APPROVED);
        assertThat(membership.isActive()).isTrue();
    }

    @Test
    void recusaPreservaHistoricoComoRejected() {
        User owner = createUser("owner-rej@teste.com");
        Character ownerChar = createCharacter("Owner Rej", "Antica", owner);
        stubPremium("Owner Rej", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.MANUAL_APPROVAL);

        User joiner = createUser("joiner-rej@teste.com");
        Character joinerChar = createCharacter("Joiner Rej", "Antica", joiner);
        stubPremium("Joiner Rej", "Antica");
        listService.joinByShareCode(joiner.getId(), team.summary().shareCode(),
                new JoinListRequest(joinerChar.getId()));
        var pending = listService.listPendingRequests(owner.getId(), team.summary().id());

        listService.rejectJoinRequest(owner.getId(), team.summary().id(), pending.get(0).id());

        var membership = membershipRepository.findById(pending.get(0).id()).orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REJECTED);
        assertThat(membership.isActive()).isFalse();
    }

    @Test
    void rejeitaPersonagemDeOutroWorld() {
        User owner = createUser("owner-world@teste.com");
        Character ownerChar = createCharacter("Owner World", "Antica", owner);
        stubPremium("Owner World", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.AUTO_ACCEPT);

        User joiner = createUser("joiner-world@teste.com");
        Character joinerChar = createCharacter("Joiner World", "Bona", joiner);
        stubPremium("Joiner World", "Bona"); // world diferente do time (Antica)

        assertThatThrownBy(() -> listService.joinByShareCode(
                joiner.getId(), team.summary().shareCode(), new JoinListRequest(joinerChar.getId())))
                .hasMessageContaining("world");
    }

    @Test
    void rejeitaPersonagemFreeAccount() {
        User owner = createUser("owner-free@teste.com");
        Character ownerChar = createCharacter("Owner Free", "Antica", owner);
        stubPremium("Owner Free", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.AUTO_ACCEPT);

        User joiner = createUser("joiner-free@teste.com");
        Character joinerChar = createCharacter("Joiner Free", "Antica", joiner);
        stubFreeAccount("Joiner Free", "Antica");

        assertThatThrownBy(() -> listService.joinByShareCode(
                joiner.getId(), team.summary().shareCode(), new JoinListRequest(joinerChar.getId())))
                .hasMessageContaining("Free Account");
    }

    @Test
    void rejeitaSextoMembroQuandoTimeEstaCheio() {
        User owner = createUser("owner-full@teste.com");
        Character ownerChar = createCharacter("Owner Full", "Antica", owner);
        stubPremium("Owner Full", "Antica");
        ListDetailResponse team = createTeam(owner, ownerChar, JoinPolicy.AUTO_ACCEPT);

        // owner já é 1 membro; adiciona mais 4 (total 5 = cheio).
        for (int i = 1; i <= 4; i++) {
            User u = createUser("m" + i + "@teste.com");
            Character c = createCharacter("Membro " + i, "Antica", u);
            stubPremium("Membro " + i, "Antica");
            listService.joinByShareCode(u.getId(), team.summary().shareCode(), new JoinListRequest(c.getId()));
        }

        User sixth = createUser("sexto@teste.com");
        Character sixthChar = createCharacter("Sexto Membro", "Antica", sixth);
        stubPremium("Sexto Membro", "Antica");

        assertThatThrownBy(() -> listService.joinByShareCode(
                sixth.getId(), team.summary().shareCode(), new JoinListRequest(sixthChar.getId())))
                .hasMessageContaining("cheio");
    }

    private ListDetailResponse createTeam(User owner, Character ownerChar, JoinPolicy policy) {
        return listService.createList(owner.getId(), new CreateListRequest(
                "Time do Demon", "Antica", creature("Demon").getId(), policy, ownerChar.getId(), null, null));
    }
}
