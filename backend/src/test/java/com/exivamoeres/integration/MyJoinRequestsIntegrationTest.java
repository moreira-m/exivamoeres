package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.JoinRequestIssue;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.MyJoinRequestResponse;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Meus pedidos" — o pedido de entrada visto por **quem pediu** (item P4).
 *
 * Antes, `GET /api/lists/mine` só devolvia time de dono ou de membro aprovado: um
 * pedido `PENDING` não aparecia em lugar nenhum, e a pessoa não sabia se tinha sido
 * ignorada, recusada ou esquecida.
 *
 * Três regras que os testes prendem: **só os próprios** pedidos, cancelar usa um
 * status **próprio** (`CANCELLED` ≠ `REJECTED`, que é decisão do dono), e o aviso
 * de "provavelmente não será aprovado" sai de **dado local** — sem TibiaData.
 */
@AutoConfigureMockMvc
class MyJoinRequestsIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired ListMembershipRepository membershipRepository;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mockMvc;

    @Test
    void pedidoPendenteApareceParaQuemPediu() {
        Ctx ctx = timeComPedido("MyReqWorld", "MyReq", null, 200);

        List<MyJoinRequestResponse> pedidos = listService.listMyJoinRequests(ctx.joinerId);

        assertThat(pedidos).hasSize(1);
        MyJoinRequestResponse pedido = pedidos.get(0);
        assertThat(pedido.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(pedido.listId()).isEqualTo(ctx.listId);
        // A tela precisa dos dados do time sem uma segunda requisição por pedido.
        assertThat(pedido.targetCreatureName()).isEqualTo("Demon");
        assertThat(pedido.world()).isEqualTo("MyReqWorld");
        assertThat(pedido.characterName()).isEqualTo("MyReq Joiner");
        assertThat(pedido.requestedAt()).isNotNull();
    }

    @Test
    void pedidoAprovadoDeixaDeSerPedido() {
        Ctx ctx = timeComPedido("ApprovedReqWorld", "AppReq", null, 200);

        listService.approveJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);

        // Aprovado não é pedido: o time passa a aparecer em "meus times".
        assertThat(listService.listMyJoinRequests(ctx.joinerId)).isEmpty();
        assertThat(listService.listMyLists(ctx.joinerId))
                .extracting(s -> s.id())
                .contains(ctx.listId);
    }

    @Test
    void pedidoRecusadoApareceComoRecusado() {
        Ctx ctx = timeComPedido("RejectedReqWorld", "RejReq", null, 200);

        listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);

        // É a resposta para "fui ignorado ou recusado?" — a razão do item existir.
        assertThat(listService.listMyJoinRequests(ctx.joinerId))
                .singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo(MembershipStatus.REJECTED));
    }

    @Test
    void cadaUmVeSoOsProprios() {
        Ctx ctx = timeComPedido("OwnOnlyWorld", "OwnOnly", null, 200);
        User curioso = createUser("curioso-pedidos@teste.com");

        assertThat(listService.listMyJoinRequests(curioso.getId())).isEmpty();
        assertThat(listService.listMyJoinRequests(ctx.ownerId)).isEmpty(); // o dono não "pediu"
        assertThat(listService.listMyJoinRequests(ctx.joinerId)).hasSize(1);
    }

    @Test
    void cancelarMarcaCanceladoEDesativa() {
        Ctx ctx = timeComPedido("CancelWorld", "Cancel", null, 200);

        listService.cancelMyJoinRequest(ctx.joinerId, ctx.membershipId);

        var membership = membershipRepository.findById(ctx.membershipId).orElseThrow();
        // CANCELLED e não REJECTED: quem desistiu foi o solicitante, não o dono.
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(membership.isActive()).isFalse();
    }

    @Test
    void cancelarTiraOPedidoDaListaDoDono() {
        Ctx ctx = timeComPedido("CancelOwnerWorld", "CancelOwn", null, 200);
        assertThat(listService.listPendingRequests(ctx.ownerId, ctx.listId)).hasSize(1);

        listService.cancelMyJoinRequest(ctx.joinerId, ctx.membershipId);

        assertThat(listService.listPendingRequests(ctx.ownerId, ctx.listId)).isEmpty();
        assertThat(listService.listMyJoinRequests(ctx.joinerId)).isEmpty();
    }

    @Test
    void cancelarPedidoDeOutraPessoaRetorna404() {
        Ctx ctx = timeComPedido("NotMineWorld", "NotMine", null, 200);
        User outro = createUser("outro-cancel@teste.com");

        // 404 e não 403: a existência do pedido de outra pessoa é informação dela.
        assertThatThrownBy(() -> listService.cancelMyJoinRequest(outro.getId(), ctx.membershipId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelarPedidoJaDecididoRetorna422() {
        Ctx ctx = timeComPedido("AlreadyDecidedWorld", "Decided", null, 200);
        listService.rejectJoinRequest(ctx.ownerId, ctx.listId, ctx.membershipId);

        assertThatThrownBy(() -> listService.cancelMyJoinRequest(ctx.joinerId, ctx.membershipId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("não está mais pendente");
    }

    @Test
    void depoisDeCancelarDaParaPedirDeNovo() {
        String world = "ReRequestWorld";
        Ctx ctx = timeComPedido(world, "ReReq", null, 200);
        listService.cancelMyJoinRequest(ctx.joinerId, ctx.membershipId);

        // A linha é reaproveitada (UNIQUE list+character), então desistir não pode
        // deixar a pessoa trancada fora do time para sempre.
        assertThatCode(() -> listService.joinByShareCode(
                ctx.joinerId, ctx.shareCode, new JoinListRequest(ctx.joinerCharacterId)))
                .doesNotThrowAnyException();
        assertThat(listService.listMyJoinRequests(ctx.joinerId))
                .singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo(MembershipStatus.PENDING));
    }

    @Test
    void avisaQuandoOTimeSubiuOLevelMinimoDepoisDoPedido() {
        Ctx ctx = timeComPedido("IssueLevelWorld", "IssueLvl", 100, 150);

        // O dono sobe a exigência: o pedido continua pendente, mas não será aprovado.
        listService.updateList(ctx.ownerId, ctx.listId,
                new com.exivamoeres.dto.list.UpdateListRequest(null, 300, null, null, null, null));

        assertThat(listService.listMyJoinRequests(ctx.joinerId))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.issue()).isEqualTo(JoinRequestIssue.BELOW_MINIMUM_LEVEL);
                    // A tela monta o texto com estes dois números.
                    assertThat(p.minimumLevel()).isEqualTo(300);
                    assertThat(p.characterLevel()).isEqualTo(150);
                });
    }

    @Test
    void avisaQuandoOPersonagemNaoEhMaisDoWorldDoTime() {
        Ctx ctx = timeComPedido("IssueWorldWorld", "IssueW", null, 200);
        // World transfer já sincronizado no dado local (o job/claim atualiza isso).
        Character joinerChar = characterRepository.findById(ctx.joinerCharacterId).orElseThrow();
        joinerChar.setWorld("OutroWorld");
        characterRepository.save(joinerChar);

        assertThat(listService.listMyJoinRequests(ctx.joinerId))
                .singleElement()
                .satisfies(p -> assertThat(p.issue()).isEqualTo(JoinRequestIssue.WORLD_MISMATCH));
    }

    @Test
    void semProblemaAparenteOAvisoEhNulo() {
        Ctx ctx = timeComPedido("NoIssueWorld", "NoIssue", 100, 150);

        assertThat(listService.listMyJoinRequests(ctx.joinerId))
                .singleElement()
                .satisfies(p -> assertThat(p.issue()).isNull());
    }

    @Test
    void oAvisoNaoConsultaATibiaData() {
        Ctx ctx = timeComPedido("NoLookupWorld", "NoLookup", 100, 150);
        org.mockito.Mockito.clearInvocations(tibiaDataClient);

        listService.listMyJoinRequests(ctx.joinerId);

        // Listar pedidos é leitura de tela: não pode gastar cota de API externa,
        // por isso o aviso usa só o level/world já sincronizados.
        org.mockito.Mockito.verify(tibiaDataClient, org.mockito.Mockito.never())
                .fetchCharacter(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void endpointExigeLoginEDevolveApenasOsPedidosDoUsuario() throws Exception {
        Ctx ctx = timeComPedido("HttpReqWorld", "HttpReq", null, 200);

        mockMvc.perform(get("/api/lists/mine/requests"))
                .andExpect(status().isUnauthorized());

        String token = jwtService.generateAccessToken(
                userRepository.findById(ctx.joinerId).orElseThrow());
        mockMvc.perform(get("/api/lists/mine/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].listId").value(ctx.listId))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                // Contato do dono não vaza para quem ainda não é membro (regra do P2).
                .andExpect(jsonPath("$[0].contact").doesNotExist());
    }

    @Test
    void cancelarPorHttpDevolve204() throws Exception {
        Ctx ctx = timeComPedido("HttpCancelWorld", "HttpCancel", null, 200);
        String token = jwtService.generateAccessToken(
                userRepository.findById(ctx.joinerId).orElseThrow());

        mockMvc.perform(delete("/api/lists/mine/requests/" + ctx.membershipId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(membershipRepository.findById(ctx.membershipId).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.CANCELLED);
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long joinerId, Long joinerCharacterId,
                       Long membershipId, String shareCode) {
    }

    /** Time MANUAL_APPROVAL com um pedido PENDING de outro usuário. */
    private Ctx timeComPedido(String world, String prefix, Integer minimoDoTime, int levelDoCandidato) {
        User owner = createUser(prefix.toLowerCase() + "-req-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);
        ListDetailResponse time = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), JoinPolicy.MANUAL_APPROVAL,
                ownerChar.getId(), minimoDoTime, null, null, null, null));

        User joiner = createUser(prefix.toLowerCase() + "-req-joiner@teste.com");
        Character joinerChar = createCharacter(prefix + " Joiner", world, joiner);
        stubPremium(prefix + " Joiner", world, levelDoCandidato, "Royal Paladin");
        ListDetailResponse comPedido = listService.joinByShareCode(
                joiner.getId(), time.summary().shareCode(), new JoinListRequest(joinerChar.getId()));

        Long membershipId = comPedido.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING && m.userId().equals(joiner.getId()))
                .findFirst().orElseThrow().id();
        return new Ctx(time.summary().id(), owner.getId(), joiner.getId(), joinerChar.getId(),
                membershipId, time.summary().shareCode());
    }
}
