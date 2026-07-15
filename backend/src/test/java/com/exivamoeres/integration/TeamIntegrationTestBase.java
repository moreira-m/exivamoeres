package com.exivamoeres.integration;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.Creature;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Base dos testes de time: Postgres real + TibiaDataClient mockado (controla
 * world e status Free/Premium sem depender da API externa).
 */
abstract class TeamIntegrationTestBase extends IntegrationTestBase {

    @MockBean
    protected TibiaDataClient tibiaDataClient;

    @Autowired protected UserRepository userRepository;
    @Autowired protected CharacterRepository characterRepository;
    @Autowired protected CreatureRepository creatureRepository;
    @Autowired protected CacheManager cacheManager;

    @BeforeEach
    void resetEligibilityCache() {
        // Evita que o snapshot mockado de um teste vaze para o próximo.
        var cache = cacheManager.getCache("characterEligibility");
        if (cache != null) {
            cache.clear();
        }
    }

    protected User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Jogador " + email);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante");
        return userRepository.save(user);
    }

    protected Character createCharacter(String name, String world, User owner) {
        Character character = new Character();
        character.setName(name);
        character.setWorld(world);
        character.setVocation("Elder Druid");
        character.setOwner(owner);
        return characterRepository.save(character);
    }

    protected Creature creature(String name) {
        return creatureRepository.findByNameIgnoreCase(name).orElseThrow();
    }

    /** Faz o TibiaData mockado devolver este personagem como Premium no world dado. */
    protected void stubPremium(String name, String world) {
        when(tibiaDataClient.fetchCharacter(name)).thenReturn(Mono.just(
                new TibiaCharacterSnapshot(true, name, world, "", "Premium Account", "Elder Druid")));
    }

    protected void stubFreeAccount(String name, String world) {
        when(tibiaDataClient.fetchCharacter(name)).thenReturn(Mono.just(
                new TibiaCharacterSnapshot(true, name, world, "", "Free Account", "Knight")));
    }

    protected void stubAnyPremium() {
        when(tibiaDataClient.fetchCharacter(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return Mono.just(new TibiaCharacterSnapshot(
                    true, name, "Antica", "", "Premium Account", "Elder Druid"));
        });
    }
}
