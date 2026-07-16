package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.Plan;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.service.ChatService;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.SoulcoreService;
import com.exivamoeres.service.TeamLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ciclo de vida dos times: limite por plano, expiração/arquivamento, conclusão, renovação e congelamento. */
class TeamLifecycleIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired SoulcoreService soulcoreService;
    @Autowired ChatService chatService;
    @Autowired TeamLifecycleService lifecycleService;
    @Autowired HuntingListRepository listRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void contaFreeNaoCriaAlemDoLimiteDeTimesAtivos() {
        User owner = createUser("free-limit@teste.com"); // default FREE
        // Limite free = 3 (application.yml). Cria 3, o 4º é barrado.
        for (int i = 1; i <= 3; i++) {
            createTeam(owner, "Char " + i, "Time " + i);
        }
        assertThatThrownBy(() -> createTeam(owner, "Char 4", "Time 4"))
                .hasMessageContaining("limite");
    }

    @Test
    void contaPremiumCriaAlemDoLimiteFree() {
        User owner = createUser("premium@teste.com");
        owner.setPlan(Plan.PREMIUM);
        userRepository.save(owner);

        for (int i = 1; i <= 5; i++) {
            createTeam(owner, "Prem Char " + i, "Prem Time " + i);
        }
        long active = listRepository.countByOwnerIdAndStatus(owner.getId(), TeamStatus.ACTIVE);
        assertThat(active).isEqualTo(5);
    }

    @Test
    void timePremiumTemPrazoMaiorQueFree() {
        User free = createUser("free-dur@teste.com");
        ListDetailResponse freeTeam = createTeam(free, "Free Dur", "Free Dur Team");

        User premium = createUser("prem-dur@teste.com");
        premium.setPlan(Plan.PREMIUM);
        userRepository.save(premium);
        ListDetailResponse premTeam = createTeam(premium, "Prem Dur", "Prem Dur Team");

        // Premium (30d) expira bem depois do free (7d).
        assertThat(premTeam.summary().expiresAt()).isAfter(freeTeam.summary().expiresAt());
    }

    @Test
    void jobArquivaTimeExpirado() {
        User owner = createUser("expire@teste.com");
        ListDetailResponse team = createTeam(owner, "Expire Char", "Expire Team");
        // Envelhece o prazo direto no banco (expires_at é imutável pela API).
        jdbcTemplate.update("UPDATE hunting_lists SET expires_at = now() - INTERVAL '1 hour' WHERE id = ?",
                team.summary().id());

        int archived = lifecycleService.archiveExpiredTeams();

        assertThat(archived).isGreaterThanOrEqualTo(1);
        HuntingList reloaded = listRepository.findById(team.summary().id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TeamStatus.ARCHIVED);
    }

    @Test
    void desbloquearOCoreDaCriaturaAlvoConcluiOTime() {
        User owner = createUser("complete@teste.com");
        Character ownerChar = createCharacter("Complete Char", "Antica", owner);
        stubPremium("Complete Char", "Antica");
        // Alvo = Demon; desbloquear Demon conclui o time.
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Complete Team", "Antica", creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));

        soulcoreService.markUnlocked(owner.getId(), team.summary().id(),
                creature("Demon").getId(), ownerChar.getId());

        HuntingList reloaded = listRepository.findById(team.summary().id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TeamStatus.COMPLETED);
    }

    @Test
    void timeArquivadoNaoAceitaEscritaDeSoulcoreNemChat() {
        User owner = createUser("frozen@teste.com");
        Character ownerChar = createCharacter("Frozen Char", "Antica", owner);
        stubPremium("Frozen Char", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Frozen Team", "Antica", creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT, ownerChar.getId(), null, null));
        Long listId = team.summary().id();
        // Arquiva.
        jdbcTemplate.update("UPDATE hunting_lists SET expires_at = now() - INTERVAL '1 hour' WHERE id = ?", listId);
        lifecycleService.archiveExpiredTeams();

        assertThatThrownBy(() -> soulcoreService.markObtained(
                owner.getId(), listId, creature("Dragon").getId(), ownerChar.getId()))
                .hasMessageContaining("arquivado");
        assertThatThrownBy(() -> chatService.sendMessage(
                owner.getId(), listId, ownerChar.getId(), "opa"))
                .hasMessageContaining("arquivado");
    }

    @Test
    void donoRenovaTimeArquivadoQuandoTemVaga() {
        User owner = createUser("renew@teste.com");
        ListDetailResponse team = createTeam(owner, "Renew Char", "Renew Team");
        Long listId = team.summary().id();
        jdbcTemplate.update("UPDATE hunting_lists SET expires_at = now() - INTERVAL '1 hour' WHERE id = ?", listId);
        lifecycleService.archiveExpiredTeams();

        ListDetailResponse renewed = listService.renewTeam(owner.getId(), listId);

        assertThat(renewed.summary().status()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(renewed.summary().expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void renovarEhBloqueadoQuandoFreeJaEstaNoLimite() {
        User owner = createUser("renew-full@teste.com");
        // 1 time que vamos arquivar + 3 ativos = free no limite ao renovar.
        ListDetailResponse toArchive = createTeam(owner, "Arch Char", "Arch Team");
        Long archivedId = toArchive.summary().id();
        jdbcTemplate.update("UPDATE hunting_lists SET expires_at = now() - INTERVAL '1 hour' WHERE id = ?", archivedId);
        lifecycleService.archiveExpiredTeams();
        for (int i = 1; i <= 3; i++) {
            createTeam(owner, "Fill Char " + i, "Fill Team " + i);
        }

        assertThatThrownBy(() -> listService.renewTeam(owner.getId(), archivedId))
                .hasMessageContaining("limite");
    }

    @Test
    void buscaExcluiArquivadosEColocaPremiumNaFrente() {
        // World isolado para não colidir com times de outros testes na base compartilhada.
        String world = "SearchTestWorld";

        User freeOwner = createUser("search-free@teste.com");
        ListDetailResponse freeTeam = createTeamInWorld(freeOwner, "Search Free", "Free Search", world);

        User premiumOwner = createUser("search-prem@teste.com");
        premiumOwner.setPlan(Plan.PREMIUM);
        userRepository.save(premiumOwner);
        ListDetailResponse premiumTeam = createTeamInWorld(premiumOwner, "Search Prem", "Prem Search", world);

        User archivedOwner = createUser("search-arch@teste.com");
        ListDetailResponse archivedTeam = createTeamInWorld(archivedOwner, "Search Arch", "Arch Search", world);
        jdbcTemplate.update("UPDATE hunting_lists SET expires_at = now() - INTERVAL '1 hour' WHERE id = ?",
                archivedTeam.summary().id());
        lifecycleService.archiveExpiredTeams();

        var results = listService.search(world, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();

        // Arquivado fora; premium antes do free.
        assertThat(results).extracting(r -> r.id())
                .contains(freeTeam.summary().id(), premiumTeam.summary().id())
                .doesNotContain(archivedTeam.summary().id());
        assertThat(results.get(0).id()).isEqualTo(premiumTeam.summary().id());
        assertThat(results.get(0).featured()).isTrue();
    }

    private ListDetailResponse createTeam(User owner, String characterName, String teamName) {
        return createTeamInWorld(owner, characterName, teamName, "Antica");
    }

    private ListDetailResponse createTeamInWorld(User owner, String characterName, String teamName, String world) {
        Character character = createCharacter(characterName, world, owner);
        stubPremium(characterName, world);
        return listService.createList(owner.getId(), new CreateListRequest(
                teamName, world, creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT, character.getId(), null, null));
    }
}
