package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Busca pública da home: filtros e paginação.
 *
 * Cada teste usa um world próprio — a base é compartilhada entre as classes de
 * teste, e a busca é global por natureza.
 */
class TeamSearchIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;

    @Test
    void paginaSemRepetirNemPularTimes() {
        String world = "PagingWorld";
        List<Long> created = createTeams(world, "Pag", 5).stream()
                .map(t -> t.summary().id())
                .toList();

        Page<ListSummaryResponse> firstPage = search(world, null, null, 0, 2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < firstPage.getTotalPages(); page++) {
            search(world, null, null, page, 2).getContent().forEach(s -> seen.add(s.id()));
        }
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void filtroDeVagaAbertaExcluiTimeCheioEContaCerto() {
        String world = "OpenSlotsWorld";
        ListDetailResponse fullTeam = createTeams(world, "Cheio", 1).get(0);
        fillTeam(fullTeam, world, "Cheio");
        List<Long> withOpenSlots = createTeams(world, "Vago", 2).stream()
                .map(t -> t.summary().id())
                .toList();

        assertThat(search(world, null, null, 0, 20).getTotalElements()).isEqualTo(3);

        Page<ListSummaryResponse> open = search(world, null, true, 0, 20);

        // O filtro roda na query: o total é o total de verdade. Quando ele era
        // aplicado em memória sobre a página, totalElements mentia e a home
        // não tinha como saber se havia mais uma página.
        assertThat(open.getTotalElements()).isEqualTo(2);
        assertThat(open.getContent()).extracting(ListSummaryResponse::id)
                .containsExactlyInAnyOrderElementsOf(withOpenSlots)
                .doesNotContain(fullTeam.summary().id());
    }

    @Test
    void filtroDeVagaAbertaSobreviveAPaginacao() {
        String world = "OpenSlotsPagingWorld";
        ListDetailResponse fullTeam = createTeams(world, "Lotado", 1).get(0);
        fillTeam(fullTeam, world, "Lotado");
        List<Long> withOpenSlots = createTeams(world, "Livre", 2).stream()
                .map(t -> t.summary().id())
                .toList();

        // Uma página por time: o time cheio não pode consumir a vaga de ninguém
        // na paginação, nem gerar uma página vazia no meio do caminho.
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            Page<ListSummaryResponse> result = search(world, null, true, page, 1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(1);
            seen.add(result.getContent().get(0).id());
        }
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(withOpenSlots);
        assertThat(seen).doesNotContain(fullTeam.summary().id());
    }

    @Test
    void filtraPorWorldECriatura() {
        String world = "FilterWorld";
        String otherWorld = "FilterOtherWorld";
        Long demonTeam = createTeam(world, "Filtro Demon", "Demon").summary().id();
        Long dragonTeam = createTeam(world, "Filtro Dragon", "Dragon Lord").summary().id();
        Long otherWorldTeam = createTeam(otherWorld, "Filtro Outro", "Demon").summary().id();

        List<Long> byWorld = ids(search(world, null, null, 0, 20));
        assertThat(byWorld).containsExactlyInAnyOrder(demonTeam, dragonTeam);

        List<Long> byCreature = ids(search(world, creature("Demon").getId(), null, 0, 20));
        assertThat(byCreature).containsExactly(demonTeam);

        // Sem world, o filtro de criatura ainda vale — e alcança o outro world.
        assertThat(ids(search(null, creature("Demon").getId(), null, 0, 50)))
                .contains(demonTeam, otherWorldTeam)
                .doesNotContain(dragonTeam);
    }

    // ----- Helpers -----

    private Page<ListSummaryResponse> search(String world, Long creatureId, Boolean hasOpenSlots,
                                             int page, int size) {
        return listService.search(world, creatureId, hasOpenSlots, PageRequest.of(page, size));
    }

    private List<Long> ids(Page<ListSummaryResponse> page) {
        return page.getContent().stream().map(ListSummaryResponse::id).toList();
    }

    /**
     * Um dono por time: a conta free só mantém 3 times ativos, e o objetivo
     * aqui é o volume de times na busca, não o limite do plano.
     */
    private List<ListDetailResponse> createTeams(String world, String prefix, int count) {
        List<ListDetailResponse> teams = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            teams.add(createTeam(world, prefix + " Dono " + i, "Demon"));
        }
        return teams;
    }

    private ListDetailResponse createTeam(String world, String characterName, String creatureName) {
        User owner = createUser(characterName.toLowerCase().replace(' ', '-') + "@teste.com");
        Character character = createCharacter(characterName, world, owner);
        stubPremium(characterName, world);
        return listService.createList(owner.getId(), new CreateListRequest(
                null, world, creature(creatureName).getId(),
                JoinPolicy.AUTO_ACCEPT, character.getId(), null, null, null, null, null));
    }

    /** Preenche o time até o máximo de 5 membros aprovados (dono + 4). */
    private void fillTeam(ListDetailResponse team, String world, String prefix) {
        for (int i = 0; i < 4; i++) {
            String name = prefix + " Membro " + i;
            User joiner = createUser(name.toLowerCase().replace(' ', '-') + "@teste.com");
            Character character = createCharacter(name, world, joiner);
            stubPremium(name, world);
            listService.joinByShareCode(joiner.getId(), team.summary().shareCode(),
                    new JoinListRequest(character.getId()));
        }
    }
}
