package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edição do time (item P14): `PATCH /api/lists/{id}`.
 *
 * O motivo de existir da feature é o caso do contato digitado errado — daí o teste
 * `membroAprovadoVeOContatoCorrigido` ser o mais importante desta classe.
 *
 * Três regras que os testes prendem: **só o dono** edita, **só time ATIVO** aceita
 * edição, e **subir o level mínimo não expulsa ninguém** que já foi aprovado.
 */
@AutoConfigureMockMvc
class TeamEditIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired HuntingListRepository listRepository;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;

    @Test
    void donoEditaOsCamposDeTextoEOValorNovoVolta() {
        Ctx ctx = team("EditWorld", "Edit");

        ListDetailResponse updated = listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest("Nome Novo", 100, 5_000L,
                        "Descrição nova", "Sáb–Dom 15h BRT", "discord: certo#0001"));

        assertThat(updated.summary().name()).isEqualTo("Nome Novo");
        assertThat(updated.summary().minimumLevel()).isEqualTo(100);
        assertThat(updated.summary().pricePerSlot()).isEqualTo(5_000L);
        assertThat(updated.summary().description()).isEqualTo("Descrição nova");
        assertThat(updated.summary().huntSchedule()).isEqualTo("Sáb–Dom 15h BRT");
        assertThat(updated.contact()).isEqualTo("discord: certo#0001");
    }

    @Test
    void membroAprovadoVeOContatoCorrigido() {
        String world = "TypoWorld";
        Ctx ctx = team(world, "Typo", "discord: erradoo#9999");

        // Membro entra (AUTO_ACCEPT) e vê o contato com o erro de digitação.
        User membro = createUser("membro-edit@teste.com");
        Character membroChar = createCharacter("Typo Membro", world, membro);
        stubPremium("Typo Membro", world);
        listService.joinByShareCode(membro.getId(), ctx.shareCode, new JoinListRequest(membroChar.getId()));
        assertThat(listService.getList(ctx.listId, membro.getId()).contact())
                .isEqualTo("discord: erradoo#9999");

        // O dono corrige — é o caso que motivou a feature: antes, a única saída
        // era encerrar o time e perder chat, histórico e membros.
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, null, null, "discord: certo#1234"));

        assertThat(listService.getList(ctx.listId, membro.getId()).contact())
                .isEqualTo("discord: certo#1234");
    }

    @Test
    void naoDonoNaoPodeEditar() {
        Ctx ctx = team("NotOwnerWorld", "NotOwner");
        User outro = createUser("nao-dono-edit@teste.com");

        assertThatThrownBy(() -> listService.updateList(outro.getId(), ctx.listId,
                new UpdateListRequest(null, null, null, "invadindo", null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void tituloEmBrancoVoltaParaONomeDaCriatura() {
        Ctx ctx = team("TitleWorld", "Title");

        ListDetailResponse updated = listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest("   ", null, null, null, null, null));

        // Mesma regra da criação: o time é identificado pela criatura-alvo.
        assertThat(updated.summary().name()).isEqualTo(creature("Demon").getName());
    }

    @Test
    void campoEmBrancoLimpaOValorAnterior() {
        String world = "ClearWorld";
        Ctx ctx = team(world, "Clear", "discord: some#0000");
        assertThat(listService.getList(ctx.listId, ctx.ownerId).contact()).isNotNull();

        // O payload é o conjunto completo: branco significa limpar, não "não mexer".
        ListDetailResponse updated = listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, "  ", "", null));

        assertThat(updated.summary().description()).isNull();
        assertThat(updated.summary().huntSchedule()).isNull();
        assertThat(updated.contact()).isNull();
    }

    @Test
    void timeArquivadoNaoAceitaEdicao() {
        Ctx ctx = team("ArchivedEditWorld", "Arch");
        arquivar(ctx.listId);

        assertThatThrownBy(() -> listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, "tarde demais", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ARCHIVED");
    }

    @Test
    void timeEncerradoNaoAceitaEdicao() {
        Ctx ctx = team("ClosedEditWorld", "Closed");
        listService.deleteTeam(ctx.ownerId, ctx.listId);

        assertThatThrownBy(() -> listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, "não deveria", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void subirLevelMinimoNaoRemoveQuemJaFoiAprovado() {
        String world = "LevelRaiseWorld";
        // Mínimo baixo na criação (o dono é level 500 pelo stub) e um membro de 150.
        Ctx ctx = team(world, "Raise", null, 100);

        User veterano = createUser("veterano-edit@teste.com");
        Character vetChar = createCharacter("Raise Membro", world, veterano);
        stubPremium("Raise Membro", world, 150, "Royal Paladin");
        listService.joinByShareCode(veterano.getId(), ctx.shareCode, new JoinListRequest(vetChar.getId()));

        // Dono sobe a exigência para 300: quem já entrou com 150 continua no time.
        // Elegibilidade sempre foi validada na ENTRADA e nunca reavaliada depois;
        // reavaliar aqui transformaria editar anúncio em expulsão em massa.
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 300, null, null, null, null));

        ListDetailResponse detail = listService.getList(ctx.listId, ctx.ownerId);
        assertThat(detail.summary().minimumLevel()).isEqualTo(300);
        assertThat(detail.members())
                .filteredOn(m -> m.active() && m.status() == MembershipStatus.APPROVED)
                .extracting(m -> m.characterName())
                .contains("Raise Membro");
    }

    @Test
    void donoNaoPodeExigirLevelQueSeuProprioPersonagemNaoTem() {
        // Personagem do dono com level conhecido e baixo.
        Ctx ctx = team("OwnerLevelWorld", "OwnLvl", null, 50);
        Character ownerChar = characterRepository.findById(ctx.ownerCharacterId).orElseThrow();
        ownerChar.setLevel(60);
        characterRepository.save(ownerChar);

        assertThatThrownBy(() -> listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 500, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("level 60");
    }

    @Test
    void worldCriaturaEPoliticaNaoMudamMesmoEnviadosNoJson() throws Exception {
        String world = "ImmutableWorld";
        Ctx ctx = team(world, "Immutable");
        Long creatureIdOriginal = creature("Demon").getId();
        String token = jwtService.generateAccessToken(
                userRepository.findById(ctx.ownerId).orElseThrow());

        mockMvc.perform(patch("/api/lists/" + ctx.listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"só a descrição muda",
                                 "world":"OutroWorld","targetCreatureId":1,
                                 "joinPolicy":"MANUAL_APPROVAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.world").value(world))
                .andExpect(jsonPath("$.summary.joinPolicy").value("AUTO_ACCEPT"));

        HuntingList reloaded = listRepository.findById(ctx.listId).orElseThrow();
        assertThat(reloaded.getWorld()).isEqualTo(world);
        assertThat(reloaded.getTargetCreature().getId()).isEqualTo(creatureIdOriginal);
        assertThat(reloaded.getJoinPolicy()).isEqualTo(JoinPolicy.AUTO_ACCEPT);
        assertThat(reloaded.getDescription()).isEqualTo("só a descrição muda");
    }

    @Test
    void edicaoSemAutenticacaoRetorna401() throws Exception {
        Ctx ctx = team("UnauthEditWorld", "Unauth");

        mockMvc.perform(patch("/api/lists/" + ctx.listId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"sem token"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void descricaoAcimaDoLimiteRetorna400() throws Exception {
        Ctx ctx = team("ValidationEditWorld", "Valid");
        String token = jwtService.generateAccessToken(
                userRepository.findById(ctx.ownerId).orElseThrow());

        mockMvc.perform(patch("/api/lists/" + ctx.listId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s"}
                                """.formatted("x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty());
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long ownerCharacterId, String shareCode) {
    }

    private Ctx team(String world, String prefix) {
        return team(world, prefix, null, null);
    }

    private Ctx team(String world, String prefix, String contact) {
        return team(world, prefix, contact, null);
    }

    private Ctx team(String world, String prefix, String contact, Integer minimumLevel) {
        User owner = createUser(prefix.toLowerCase() + "-edit-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);

        ListDetailResponse detail = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT,
                ownerChar.getId(), minimumLevel, null, null, null, contact, null));
        return new Ctx(detail.summary().id(), owner.getId(), ownerChar.getId(),
                detail.summary().shareCode());
    }

    /** Envelhece o prazo e roda o arquivamento pelo caminho real (status é imutável pela API). */
    private void arquivar(Long listId) {
        jdbcTemplate.update("UPDATE hunting_lists SET status = ?, expires_at = now() - INTERVAL '1 hour' WHERE id = ?",
                TeamStatus.ARCHIVED.name(), listId);
    }
}
