package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.TeamSlotResponse;
import com.exivamoeres.dto.list.UpdateSlotsRequest;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Composição do time por vocação (item P3): "faltam 1 EK e 1 ED".
 *
 * Três regras que os testes prendem: **time sem composição não muda de
 * comportamento** (é o estado de tudo que existia antes), a vaga é atribuída na
 * **aprovação** (pedido não reserva), e **vaga ocupada não muda** de exigência.
 *
 * Cada teste usa um world próprio — a base é compartilhada entre as classes.
 */
class TeamSlotIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;

    @Test
    void criarComComposicaoGravaAsVagasEColocaODonoNaCompativel() {
        // Dono é Elder Druid → tem que cair na vaga de DRUID, não na livre.
        Ctx ctx = time("SlotCreateWorld", "SlotC", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID, null));

        List<TeamSlotResponse> slots = ctx.detail.summary().slots();
        assertThat(slots).hasSize(3);
        assertThat(slots).extracting(TeamSlotResponse::vocation)
                .containsExactly(Vocation.KNIGHT, Vocation.DRUID, null);
        assertThat(slots.get(1).characterName()).isEqualTo("SlotC Dono");
        // A vaga de Knight e a livre continuam abertas.
        assertThat(slots.get(0).characterName()).isNull();
        assertThat(slots.get(2).characterName()).isNull();
    }

    @Test
    void vagaComExigenciaEhPreferidaAVagaLivre() {
        // Se o Druid consumisse a vaga livre, o time anunciaria que falta Druid
        // quando não falta — e a vaga de Druid ficaria sobrando.
        Ctx ctx = time("PreferWorld", "Prefer", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(null, Vocation.DRUID));

        assertThat(ctx.detail.summary().slots().get(1).characterName()).isEqualTo("Prefer Dono");
        assertThat(ctx.detail.summary().slots().get(0).characterName()).isNull();
    }

    @Test
    void criarComComposicaoQueNaoCabeODonoEhRecusado() {
        User owner = createUser("slot-nofit@teste.com");
        Character ownerChar = createCharacter("Slot NoFit Dono", "NoFitWorld", owner);
        stubPremium("Slot NoFit Dono", "NoFitWorld", 300, "Elder Druid");

        assertThatThrownBy(() -> listService.createList(owner.getId(), new CreateListRequest(
                "NoFit Team", "NoFitWorld", creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT,
                ownerChar.getId(), null, null, null, null, null,
                List.of(Vocation.KNIGHT, Vocation.PALADIN))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DRUID");
    }

    @Test
    void timeSemComposicaoAceitaQualquerVocacao() {
        // O estado de todos os times criados antes da V17: nada muda para eles.
        Ctx ctx = time("NoSlotsWorld", "NoSlots", JoinPolicy.AUTO_ACCEPT, "Elder Druid", null);
        assertThat(ctx.detail.summary().slots()).isEmpty();

        assertThatCode(() -> entrar(ctx, "NoSlots Knight", "Elite Knight"))
                .doesNotThrowAnyException();
    }

    @Test
    void composicaoSoComVagasLivresEhTratadaComoSemComposicao() {
        // Cinco vagas livres se comportam exatamente como time sem vaga; não vale
        // gravar linhas para isso.
        Ctx ctx = time("AllFreeWorld", "AllFree", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(null, null, null));

        assertThat(ctx.detail.summary().slots()).isEmpty();
    }

    @Test
    void entrarOcupaAVagaCompativel() {
        Ctx ctx = time("JoinSlotWorld", "JoinS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        ListDetailResponse depois = entrar(ctx, "JoinS Knight", "Elite Knight");

        assertThat(depois.summary().slots().get(0).characterName()).isEqualTo("JoinS Knight");
        assertThat(depois.summary().slots().get(0).characterVocation()).isEqualTo(Vocation.KNIGHT);
    }

    @Test
    void entrarSemVagaLivreCompativelEhRecusado() {
        Ctx ctx = time("FullVocWorld", "FullVoc", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));
        entrar(ctx, "FullVoc Knight", "Elite Knight"); // ocupa a única vaga de Knight

        assertThatThrownBy(() -> entrar(ctx, "FullVoc Knight Dois", "Knight"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Não há vaga livre");
    }

    @Test
    void pedirParaVocacaoQueACompsicaoNaoPreveEhRecusadoNoPedido() {
        Ctx ctx = time("NoVocWorld", "NoVoc", JoinPolicy.MANUAL_APPROVAL, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        // Sorcerer não cabe em vaga nenhuma: recusar no pedido evita o limbo de
        // esperar por uma aprovação que nunca poderia acontecer.
        assertThatThrownBy(() -> entrar(ctx, "NoVoc Sorc", "Master Sorcerer"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("não tem vaga para SORCERER");
    }

    @Test
    void pedidoParaVagaOcupadaEhAceitoMasAprovacaoRecusa() {
        Ctx ctx = time("PendingSlotWorld", "PendS", JoinPolicy.MANUAL_APPROVAL, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        // Primeiro Knight pede e é aprovado — ocupa a vaga.
        ListDetailResponse comPrimeiro = entrar(ctx, "PendS Knight Um", "Elite Knight");
        listService.approveJoinRequest(ctx.ownerId, ctx.listId, pendenteDe(comPrimeiro, "PendS Knight Um"));

        // Segundo Knight pede: aceito (alguém pode sair — igual ao time cheio)...
        ListDetailResponse comSegundo = entrar(ctx, "PendS Knight Dois", "Knight");
        Long pedidoDoSegundo = pendenteDe(comSegundo, "PendS Knight Dois");

        // ...mas a aprovação recusa enquanto a vaga estiver ocupada.
        assertThatThrownBy(() -> listService.approveJoinRequest(ctx.ownerId, ctx.listId, pedidoDoSegundo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Não há vaga livre");
    }

    @Test
    void sairLiberaAVagaParaOutroDaMesmaVocacao() {
        Ctx ctx = time("FreeSlotWorld", "FreeS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));
        ListDetailResponse comKnight = entrar(ctx, "FreeS Knight", "Elite Knight");
        Long userDoKnight = comKnight.members().stream()
                .filter(m -> m.characterName().equals("FreeS Knight"))
                .findFirst().orElseThrow().userId();

        listService.leaveList(userDoKnight, ctx.listId);

        // A vaga volta a contar como livre (o slot_id fica na membership inativa,
        // preservando o histórico de "esteve na vaga 1").
        assertThatCode(() -> entrar(ctx, "FreeS Knight Dois", "Knight"))
                .doesNotThrowAnyException();
        assertThat(listService.getList(ctx.listId, null).summary().slots().get(0).characterName())
                .isEqualTo("FreeS Knight Dois");
    }

    @Test
    void expulsarLiberaAVaga() {
        Ctx ctx = time("KickSlotWorld", "KickS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));
        ListDetailResponse comKnight = entrar(ctx, "KickS Knight", "Elite Knight");
        Long membership = comKnight.members().stream()
                .filter(m -> m.characterName().equals("KickS Knight"))
                .findFirst().orElseThrow().id();

        listService.kickMember(ctx.ownerId, ctx.listId, membership);

        assertThat(listService.getList(ctx.listId, null).summary().slots().get(0).characterName())
                .isNull();
    }

    @Test
    void personagemSemVocacaoSoEntraEmVagaLivre() {
        Ctx comLivre = time("NoneFreeWorld", "NoneF", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.DRUID, null));
        assertThatCode(() -> entrar(comLivre, "NoneF Novato", "None"))
                .doesNotThrowAnyException();

        Ctx soRestrita = time("NoneRestrictWorld", "NoneR", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.DRUID, Vocation.KNIGHT));
        assertThatThrownBy(() -> entrar(soRestrita, "NoneR Novato", "None"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aBuscaTrazAComposicaoParaOCardDizerOQueFalta() {
        String world = "SearchSlotWorld";
        time(world, "SearchS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID, Vocation.PALADIN));

        var pagina = listService.search(world, null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).singleElement().satisfies(s -> {
            assertThat(s.slots()).hasSize(3);
            // O card monta "faltam 1 EK e 1 RP" com as vagas sem ocupante.
            assertThat(s.slots()).filteredOn(slot -> slot.characterName() == null)
                    .extracting(TeamSlotResponse::vocation)
                    .containsExactly(Vocation.KNIGHT, Vocation.PALADIN);
        });
    }

    @Test
    void donoTrocaAExigenciaDeVagaVazia() {
        Ctx ctx = time("ReplaceSlotWorld", "ReplS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        ListDetailResponse depois = listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(Arrays.asList(Vocation.PALADIN, Vocation.DRUID)));

        assertThat(depois.summary().slots()).extracting(TeamSlotResponse::vocation)
                .containsExactly(Vocation.PALADIN, Vocation.DRUID);
    }

    @Test
    void aComposicaoNovaNaoPodeDeixarMembroDeFora() {
        Ctx ctx = time("OccupiedSlotWorld", "OccS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        // O dono é Druid e está no time: uma composição só de Knight/Paladin não tem
        // onde colocá-lo. Recusa dizendo QUEM barra, em vez de expulsar alguém ou
        // deixar o time anunciando composição que não corresponde a quem está dentro.
        assertThatThrownBy(() -> listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(Arrays.asList(Vocation.KNIGHT, Vocation.PALADIN))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DRUID")
                .hasMessageContaining("OccS Dono");
    }

    @Test
    void reordenarAComposicaoEhPermitidoSeTodosContinuamCabendo() {
        Ctx ctx = time("ReorderWorld", "Reorder", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        // O dono (Druid) está na vaga 2; invertendo a ordem ele vai para a vaga 1.
        ListDetailResponse depois = listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(Arrays.asList(Vocation.DRUID, Vocation.KNIGHT)));

        assertThat(depois.summary().slots().get(0).characterName()).isEqualTo("Reorder Dono");
        assertThat(depois.summary().slots().get(1).characterName()).isNull();
    }

    @Test
    void removerAComposicaoLiberaTodasAsVagas() {
        Ctx ctx = time("RemoveSlotWorld", "RemS", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));

        // Time sem composição aceita qualquer um, então remover é sempre possível.
        ListDetailResponse depois = listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(List.of()));

        assertThat(depois.summary().slots()).isEmpty();
        // E o time volta a aceitar vocação que a composição barrava.
        assertThatCode(() -> entrar(ctx, "RemS Sorc", "Master Sorcerer"))
                .doesNotThrowAnyException();
    }

    @Test
    void configurarComposicaoEmTimeAntigoAssentaQuemJaEstaDentro() {
        // O caso de todos os times criados antes da V17: membros sem vaga nenhuma.
        // Sem reassentar, o time ficaria anunciando "faltam 2" com 2 membros dentro.
        Ctx ctx = time("LegacyWorld", "Legacy", JoinPolicy.AUTO_ACCEPT, "Elder Druid", null);
        entrar(ctx, "Legacy Knight", "Elite Knight");
        assertThat(listService.getList(ctx.listId, null).summary().slots()).isEmpty();

        ListDetailResponse depois = listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(Arrays.asList(Vocation.KNIGHT, Vocation.DRUID, null)));

        assertThat(depois.summary().slots().get(0).characterName()).isEqualTo("Legacy Knight");
        assertThat(depois.summary().slots().get(1).characterName()).isEqualTo("Legacy Dono");
        assertThat(depois.summary().slots().get(2).characterName()).isNull();
    }

    @Test
    void naoDonoNaoTrocaAComposicao() {
        Ctx ctx = time("SlotAuthWorld", "SlotAuth", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.KNIGHT, Vocation.DRUID));
        User outro = createUser("slot-nao-dono@teste.com");

        assertThatThrownBy(() -> listService.replaceSlots(outro.getId(), ctx.listId,
                new UpdateSlotsRequest(List.of(Vocation.PALADIN))))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void composicaoMaiorQueOTimeEhRecusada() {
        Ctx ctx = time("TooManyWorld", "TooMany", JoinPolicy.AUTO_ACCEPT, "Elder Druid",
                Arrays.asList(Vocation.DRUID));

        assertThatThrownBy(() -> listService.replaceSlots(ctx.ownerId, ctx.listId,
                new UpdateSlotsRequest(List.of(Vocation.DRUID, Vocation.KNIGHT, Vocation.KNIGHT,
                        Vocation.KNIGHT, Vocation.KNIGHT, Vocation.KNIGHT))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mais de 5");
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, String shareCode, ListDetailResponse detail) {
    }

    private Ctx time(String world, String prefix, JoinPolicy policy, String vocacaoDoDono,
                     List<Vocation> slots) {
        User owner = createUser(prefix.toLowerCase() + "-slot-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world, 300, vocacaoDoDono);
        ListDetailResponse detail = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), policy, ownerChar.getId(),
                null, null, null, null, null, slots));
        return new Ctx(detail.summary().id(), owner.getId(), detail.summary().shareCode(), detail);
    }

    /** Cria um usuário/personagem com a vocação dada e entra (ou pede para entrar). */
    private ListDetailResponse entrar(Ctx ctx, String characterName, String vocacao) {
        String world = ctx.detail.summary().world();
        User user = createUser(characterName.toLowerCase().replace(' ', '-') + "@teste.com");
        Character character = createCharacter(characterName, world, user);
        stubPremium(characterName, world, 300, vocacao);
        return listService.joinByShareCode(user.getId(), ctx.shareCode,
                new JoinListRequest(character.getId()));
    }

    private Long pendenteDe(ListDetailResponse detail, String characterName) {
        return detail.members().stream()
                .filter(m -> m.characterName().equals(characterName)
                        && m.status() == MembershipStatus.PENDING)
                .findFirst().orElseThrow().id();
    }
}
