package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Descrição, horário da caçada e contato do time (item P2).
 *
 * O que estes testes protegem é a assimetria entre os três campos: descrição e
 * horário são **públicos** de propósito (é a informação que faz alguém escolher
 * o time), e o contato é **dado pessoal** — só o dono e membros APROVADOS veem.
 * A regra vive no servidor porque `GET /api/lists/{id}` é público.
 *
 * Cada teste usa um world próprio: a base é compartilhada entre as classes.
 */
@AutoConfigureMockMvc
class TeamProfileIntegrationTest extends TeamIntegrationTestBase {

    private static final String DESCRIPTION = "Trazer 200 SDs. Loot dividido no fim da hunt.";
    private static final String SCHEDULE = "Seg–Sex 20h BRT";
    private static final String CONTACT = "discord: exiva#1234";

    @Autowired HuntingListService listService;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mockMvc;

    @Test
    void criarTimeGuardaDescricaoHorarioEContato() {
        Ctx ctx = teamWith("ProfileWorld", "Perfil", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.AUTO_ACCEPT);

        var summary = ctx.detail.summary();
        assertThat(summary.description()).isEqualTo(DESCRIPTION);
        assertThat(summary.huntSchedule()).isEqualTo(SCHEDULE);
        // O dono recebe o próprio contato de volta.
        assertThat(ctx.detail.contact()).isEqualTo(CONTACT);
    }

    @Test
    void camposEmBrancoNaoViramTextoVazio() {
        // Formulário enviado com os campos opcionais vazios: nulo, não "".
        // Se virasse "", a tela ganharia um bloco de descrição em branco.
        Ctx ctx = teamWith("BlankProfileWorld", "Branco", "   ", "\n ", "",
                JoinPolicy.AUTO_ACCEPT);

        assertThat(ctx.detail.summary().description()).isNull();
        assertThat(ctx.detail.summary().huntSchedule()).isNull();
        assertThat(ctx.detail.contact()).isNull();
    }

    @Test
    void anonimoVeDescricaoEHorarioMasNuncaOContato() {
        Ctx ctx = teamWith("AnonProfileWorld", "Anon", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.AUTO_ACCEPT);

        ListDetailResponse asAnonymous = listService.getList(ctx.listId, null);

        assertThat(asAnonymous.summary().description()).isEqualTo(DESCRIPTION);
        assertThat(asAnonymous.summary().huntSchedule()).isEqualTo(SCHEDULE);
        assertThat(asAnonymous.contact()).isNull();
    }

    @Test
    void usuarioLogadoDeFuraNaoVeOContato() {
        Ctx ctx = teamWith("OutsiderWorld", "Fora", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.AUTO_ACCEPT);
        User curioso = createUser("curioso-perfil@teste.com");

        // Estar logado não é participar: ter conta não dá acesso ao contato.
        assertThat(listService.getList(ctx.listId, curioso.getId()).contact()).isNull();
    }

    @Test
    void pedidoPendenteAindaNaoDaAcessoAoContato() {
        String world = "PendingProfileWorld";
        Ctx ctx = teamWith(world, "Pendente", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.MANUAL_APPROVAL);

        User joiner = createUser("pendente-perfil@teste.com");
        Character joinerChar = createCharacter("Pendente Char", world, joiner);
        stubPremium("Pendente Char", world);
        listService.joinByShareCode(joiner.getId(), ctx.shareCode, new JoinListRequest(joinerChar.getId()));

        // Se pedido pendente bastasse, qualquer pessoa pegaria o contato de
        // qualquer dono só clicando em "entrar".
        assertThat(listService.getList(ctx.listId, joiner.getId()).contact()).isNull();
    }

    @Test
    void membroAprovadoVeOContato() {
        String world = "ApprovedProfileWorld";
        Ctx ctx = teamWith(world, "Aprovado", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.MANUAL_APPROVAL);

        User joiner = createUser("aprovado-perfil@teste.com");
        Character joinerChar = createCharacter("Aprovado Char", world, joiner);
        stubPremium("Aprovado Char", world);
        ListDetailResponse pending = listService.joinByShareCode(
                joiner.getId(), ctx.shareCode, new JoinListRequest(joinerChar.getId()));
        Long membershipId = pending.members().stream()
                .filter(m -> m.status() == MembershipStatus.PENDING)
                .findFirst().orElseThrow().id();

        listService.approveJoinRequest(ctx.ownerId, ctx.listId, membershipId);

        assertThat(listService.getList(ctx.listId, joiner.getId()).contact()).isEqualTo(CONTACT);
    }

    @Test
    void entrarPorAutoAcceptJaDevolveOContato() {
        String world = "AutoProfileWorld";
        Ctx ctx = teamWith(world, "Auto", DESCRIPTION, SCHEDULE, CONTACT, JoinPolicy.AUTO_ACCEPT);

        User joiner = createUser("auto-perfil@teste.com");
        Character joinerChar = createCharacter("Auto Char", world, joiner);
        stubPremium("Auto Char", world);

        // AUTO_ACCEPT aprova na hora, então o contato já vem na própria resposta
        // da entrada — quem entrou não precisa recarregar a página para combinar.
        ListDetailResponse joined = listService.joinByShareCode(
                joiner.getId(), ctx.shareCode, new JoinListRequest(joinerChar.getId()));
        assertThat(joined.contact()).isEqualTo(CONTACT);
    }

    @Test
    void buscaPublicaTrazHorarioENuncaExpoeContato() throws Exception {
        String world = "SearchProfileWorld";
        teamWith(world, "Busca", DESCRIPTION, SCHEDULE, CONTACT, JoinPolicy.AUTO_ACCEPT);

        mockMvc.perform(get("/api/lists/search").param("world", world))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].huntSchedule").value(SCHEDULE))
                .andExpect(jsonPath("$.content[0].description").value(DESCRIPTION))
                // O campo não existe no DTO da busca — é a garantia mais forte
                // possível de que contato não vaza para a home pública.
                .andExpect(jsonPath("$.content[0].contact").doesNotExist());
    }

    @Test
    void detalheHttpEscondeOContatoDoAnonimoEMostraAoDono() throws Exception {
        Ctx ctx = teamWith("HttpProfileWorld", "Http", DESCRIPTION, SCHEDULE, CONTACT,
                JoinPolicy.AUTO_ACCEPT);

        // Sem Authorization: o principal é nulo no controller.
        mockMvc.perform(get("/api/lists/" + ctx.listId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.contact").doesNotExist());

        String ownerToken = jwtService.generateAccessToken(
                userRepository.findById(ctx.ownerId).orElseThrow());
        mockMvc.perform(get("/api/lists/" + ctx.listId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(CONTACT));
    }

    @Test
    void descricaoAcimaDoLimiteRetorna400() throws Exception {
        User owner = createUser("limite-perfil@teste.com");
        Character ownerChar = createCharacter("Limite Char", "LimitWorld", owner);
        stubPremium("Limite Char", "LimitWorld");
        String token = jwtService.generateAccessToken(owner);
        String longa = "x".repeat(501);

        mockMvc.perform(post("/api/lists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"world":"LimitWorld","targetCreatureId":%d,"joinPolicy":"AUTO_ACCEPT",
                                 "characterId":%d,"description":"%s"}
                                """.formatted(creature("Demon").getId(), ownerChar.getId(), longa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty());
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, String shareCode, ListDetailResponse detail) {
    }

    private Ctx teamWith(String world, String prefix, String description, String schedule,
                         String contact, JoinPolicy policy) {
        User owner = createUser(prefix.toLowerCase() + "-perfil-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);

        ListDetailResponse detail = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), policy, ownerChar.getId(),
                null, null, description, schedule, contact));
        return new Ctx(detail.summary().id(), owner.getId(), detail.summary().shareCode(), detail);
    }
}
