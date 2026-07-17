package com.exivamoeres.integration;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.CharacterLevelRefreshService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh periódico de level ponta a ponta: Postgres real (Testcontainers) +
 * TibiaData simulada (WireMock). Cobre as garantias de custo/correção:
 * personagem de time ativo com retrato vencido é atualizado; personagem fora de
 * time ativo não gera nenhuma chamada.
 */
class CharacterLevelRefreshIntegrationTest extends IntegrationTestBase {

    private static final String CHARACTER_NAME = "Sir Exiva";
    private static final String ENCODED_PATH = "/v4/character/Sir%20Exiva";

    static final WireMockServer TIBIADATA = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        TIBIADATA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.tibiadata.base-url", TIBIADATA::baseUrl);
        // Sem espaçamento entre chamadas nos testes; staleness explícito.
        registry.add("app.character.level-refresh-spacing", () -> "0s");
        registry.add("app.character.level-staleness", () -> "12h");
        registry.add("app.character.level-refresh-batch-size", () -> "15");
        registry.add("resilience4j.retry.instances.tibiadata.wait-duration", () -> "50ms");
        registry.add("resilience4j.retry.instances.tibiadata.enable-exponential-backoff", () -> "false");
    }

    @AfterAll
    static void stopWireMock() {
        TIBIADATA.stop();
    }

    @Autowired CharacterLevelRefreshService refreshService;
    @Autowired CharacterRepository characterRepository;
    @Autowired UserRepository userRepository;
    @Autowired HuntingListRepository huntingListRepository;
    @Autowired ListMembershipRepository membershipRepository;
    @Autowired CreatureRepository creatureRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        TIBIADATA.resetAll();
        membershipRepository.deleteAll();
        huntingListRepository.deleteAll();
        characterRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void atualizaLevelDePersonagemDeTimeAtivoComRetratoVencido() {
        User owner = newUser("dono@teste.com");
        Character character = newCharacter(owner, 100);
        activeMembership(owner, character);
        makeStale(character);
        stubCharacterWithLevel(250);

        refreshService.refreshStaleTeamCharacters();

        Character reloaded = characterRepository.findById(character.getId()).orElseThrow();
        assertThat(reloaded.getLevel()).isEqualTo(250);
        TIBIADATA.verify(1, getRequestedFor(urlEqualTo(ENCODED_PATH)));
    }

    @Test
    void naoAtualizaPersonagemForaDeTimeAtivo() {
        User owner = newUser("dono@teste.com");
        Character character = newCharacter(owner, 100);
        // Sem membership em time ativo: fora do escopo.
        makeStale(character);
        stubCharacterWithLevel(250);

        refreshService.refreshStaleTeamCharacters();

        Character reloaded = characterRepository.findById(character.getId()).orElseThrow();
        assertThat(reloaded.getLevel()).isEqualTo(100);
        // Nenhuma chamada deve ter saído — economia é o ponto do escopo.
        TIBIADATA.verify(0, getRequestedFor(urlEqualTo(ENCODED_PATH)));
    }

    @Test
    void naoAtualizaPersonagemComRetratoRecente() {
        User owner = newUser("dono@teste.com");
        Character character = newCharacter(owner, 100);
        activeMembership(owner, character);
        // updated_at recente (recém-salvo) => ainda dentro da validade.
        stubCharacterWithLevel(250);

        refreshService.refreshStaleTeamCharacters();

        Character reloaded = characterRepository.findById(character.getId()).orElseThrow();
        assertThat(reloaded.getLevel()).isEqualTo(100);
        TIBIADATA.verify(0, getRequestedFor(urlEqualTo(ENCODED_PATH)));
    }

    // ----- Helpers -----

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Jogador " + email);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante-para-o-teste");
        return userRepository.save(user);
    }

    private Character newCharacter(User owner, Integer level) {
        Character character = new Character();
        character.setName(CHARACTER_NAME);
        character.setWorld("Antica");
        character.setOwner(owner);
        character.setLevel(level);
        return characterRepository.save(character);
    }

    private ListMembership activeMembership(User owner, Character character) {
        HuntingList list = new HuntingList();
        list.setName("Time do Dragon");
        list.setWorld("Antica");
        list.setShareCode("SHARE123");
        list.setOwner(owner);
        list.setTargetCreature(creatureRepository.findByNameIgnoreCase("Dragon").orElseThrow());
        list.setJoinPolicy(JoinPolicy.AUTO_ACCEPT);
        list.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        huntingListRepository.save(list);

        ListMembership membership = new ListMembership();
        membership.setList(list);
        membership.setUser(owner);
        membership.setCharacter(character);
        membership.setActive(true);
        membership.setStatus(MembershipStatus.APPROVED);
        return membershipRepository.save(membership);
    }

    /** Envelhece o retrato local direto no banco (updated_at é tocado só pelo sync). */
    private void makeStale(Character character) {
        jdbcTemplate.update(
                "UPDATE characters SET updated_at = now() - INTERVAL '13 hours' WHERE id = ?",
                character.getId());
    }

    private void stubCharacterWithLevel(int level) {
        String body = """
                {
                  "character": {
                    "character": {
                      "name": "%s",
                      "world": "Antica",
                      "vocation": "Elite Knight",
                      "level": %d,
                      "account_status": "Premium Account",
                      "comment": ""
                    }
                  }
                }
                """.formatted(CHARACTER_NAME, level);
        TIBIADATA.stubFor(get(urlEqualTo(ENCODED_PATH)).willReturn(okJson(body)));
    }
}
