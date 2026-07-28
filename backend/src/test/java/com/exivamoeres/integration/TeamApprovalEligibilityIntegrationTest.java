package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Elegibilidade revalidada **na aprovação** (item P15).
 *
 * A entrada já validava world, Premium e level mínimo. O que faltava é que entre
 * **pedir** e **ser aprovado** o mundo muda: o dono sobe o level mínimo (possível
 * desde o P14), o personagem faz world transfer, perde o premium ou cai de level.
 *
 * Duas decisões que os testes prendem: o pedido recusado por inelegibilidade
 * continua **`PENDING`** (não vira `REJECTED`, que significa "o dono não quis"), e
 * **time cheio não consulta a TibiaData** — a vaga é checada antes.
 */
class TeamApprovalEligibilityIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired NotificationService notificationService;
    @Autowired ListMembershipRepository membershipRepository;

    @Test
    void aprovarRecusaQuandoODonoSubiuOLevelMinimoAcimaDoCandidato() {
        Ctx ctx = timeComPedidoPendente("ApproveLevelWorld", "AppLvl", 100, 150);

        // O dono sobe a exigência depois de o pedido já existir.
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 300, null, null, null, null));

        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("level mínimo 300")
                .hasMessageContaining("continua pendente");
    }

    @Test
    void pedidoRecusadoPorElegibilidadeContinuaPendente() {
        Ctx ctx = timeComPedidoPendente("StayPendingWorld", "Stay", 100, 150);
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 300, null, null, null, null));
        long avisosAntes = notificationService.countUnread(ctx.joinerId);

        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class);

        // Não virou REJECTED: inelegibilidade é temporária e consertável, e
        // REJECTED significa "o dono não quis" (e notificaria a pessoa disso).
        MembershipResponse pedido = pedido(ctx.listId, ctx.membershipId);
        assertThat(pedido.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(pedido.active()).isTrue();
        assertThat(notificationService.countUnread(ctx.joinerId)).isEqualTo(avisosAntes);
    }

    @Test
    void aprovarRecusaPersonagemQueTrocouDeWorld() {
        Ctx ctx = timeComPedidoPendente("TransferWorld", "Transf", null, 200);

        // World transfer depois do pedido. O cache de elegibilidade é limpo porque
        // em produção quem faz isso é o TTL (1h) — aqui o teste força o efeito.
        stubPremium("Transf Joiner", "OutroWorld", 200, "Elder Druid");
        limparCacheDeElegibilidade();

        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("OutroWorld");
    }

    @Test
    void aprovarRecusaPersonagemQueVirouFreeAccount() {
        Ctx ctx = timeComPedidoPendente("FreeAccountWorld", "FreeAcc", null, 200);

        stubFreeAccount("FreeAcc Joiner", "FreeAccountWorld");
        limparCacheDeElegibilidade();

        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Free Account");
    }

    @Test
    void aprovarSegueFuncionandoQuandoNadaMudou() {
        Ctx ctx = timeComPedidoPendente("HappyApproveWorld", "Happy", 100, 150);

        assertThatCode(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .doesNotThrowAnyException();

        assertThat(pedido(ctx.listId, ctx.membershipId).status()).isEqualTo(MembershipStatus.APPROVED);
    }

    @Test
    void recusarNaoRevalidaElegibilidade() {
        Ctx ctx = timeComPedidoPendente("RejectWorld", "Rej", 100, 150);
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 300, null, null, null, null));

        // O dono precisa conseguir limpar um pedido que nunca poderá ser aprovado.
        assertThatCode(() -> listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .doesNotThrowAnyException();
        // Recusa desativa a membership, então ela sai do detalhe — a asserção vai
        // no repositório de propósito.
        var recusado = membershipRepository.findById(ctx.membershipId).orElseThrow();
        assertThat(recusado.getStatus()).isEqualTo(MembershipStatus.REJECTED);
        assertThat(recusado.isActive()).isFalse();
    }

    @Test
    void timeCheioNaoConsultaATibiaDataAoAprovar() {
        String world = "FullTeamWorld";
        Ctx ctx = timeComPedidoPendente(world, "Full", null, 200);
        // Enche as 4 vagas restantes com outros personagens.
        for (int i = 1; i <= 4; i++) {
            User membro = createUser("full-membro-" + i + "@teste.com");
            Character chr = createCharacter("Full Membro " + i, world, membro);
            stubPremium("Full Membro " + i, world, 200, "Elder Druid");
            listService.joinByShareCode(membro.getId(), ctx.shareCode, new JoinListRequest(chr.getId()));
            aprovarUltimoPendente(ctx, membro.getId());
        }

        // A partir daqui o time está cheio: a recusa tem que vir do COUNT no banco,
        // sem gastar chamada externa.
        limparCacheDeElegibilidade();
        org.mockito.Mockito.clearInvocations(tibiaDataClient);
        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cheio");
        verify(tibiaDataClient, never()).fetchCharacter(anyString());
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long joinerId, Long membershipId, String shareCode) {
    }

    /** Time com um pedido PENDING (política MANUAL_APPROVAL) de um personagem no level dado. */
    private Ctx timeComPedidoPendente(String world, String prefix, Integer minimoDoTime, int levelDoCandidato) {
        User owner = createUser(prefix.toLowerCase() + "-aprov-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);
        ListDetailResponse time = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                ownerChar.getId(), minimoDoTime, null, null, null, null));

        User joiner = createUser(prefix.toLowerCase() + "-aprov-joiner@teste.com");
        Character joinerChar = createCharacter(prefix + " Joiner", world, joiner);
        stubPremium(prefix + " Joiner", world, levelDoCandidato, "Royal Paladin");
        ListDetailResponse comPedido = listService.joinByShareCode(
                joiner.getId(), time.summary().shareCode(), new JoinListRequest(joinerChar.getId()));

        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(joiner.getId()))
                .findFirst().orElseThrow().id();
        return new Ctx(time.summary().id(), owner.getId(), joiner.getId(), membershipId,
                time.summary().shareCode());
    }

    private void aprovarUltimoPendente(Ctx ctx, Long userId) {
        Long membershipId = listService.getList(ctx.listId, ctx.ownerId).members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(userId))
                .findFirst().orElseThrow().id();
        listService.approveJoinRequest(ctx.ownerId, ctx.listId, membershipId);
    }

    private MembershipResponse pedido(Long listId, Long membershipId) {
        return listService.getList(listId, null).members().stream()
                .filter(m -> m.id().equals(membershipId))
                .findFirst().orElseThrow();
    }

    /**
     * Em produção, quem "esquece" o snapshot antigo é o TTL do cache de
     * elegibilidade (1h). No teste, limpar é o que torna o stub novo visível.
     */
    private void limparCacheDeElegibilidade() {
        var cache = cacheManager.getCache("characterEligibility");
        if (cache != null) {
            cache.clear();
        }
    }
}
