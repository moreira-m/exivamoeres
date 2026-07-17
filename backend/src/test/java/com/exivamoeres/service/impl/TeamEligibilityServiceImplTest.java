package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regra de negócio sensível: só personagens Premium entram em times, exceto os
 * do allowlist administrativo (TEAM_PREMIUM_BYPASS_CHARACTERS).
 */
class TeamEligibilityServiceImplTest {

    private final CachedCharacterLookup lookup = mock(CachedCharacterLookup.class);
    private final CharacterSyncServiceImpl syncService = mock(CharacterSyncServiceImpl.class);

    private TeamEligibilityServiceImpl serviceWithBypass(List<String> bypass) {
        TeamProperties props = new TeamProperties(5, 3, 7, 30, Duration.ofHours(1), bypass);
        return new TeamEligibilityServiceImpl(lookup, syncService, props);
    }

    private Character character(String name) {
        Character c = new Character();
        c.setName(name);
        c.setWorld("Antica");
        return c;
    }

    private TibiaCharacterSnapshot freeAccount(String name) {
        return new TibiaCharacterSnapshot(true, name, "Antica", "", "Free Account", "Elite Knight", 200);
    }

    @Test
    void bloqueiaFreeAccountForaDoAllowlist() {
        Character character = character("Random Guy");
        when(lookup.fetch(eq("Random Guy"))).thenReturn(freeAccount("Random Guy"));

        TeamEligibilityServiceImpl service = serviceWithBypass(List.of("Meu Char"));

        assertThatThrownBy(() -> service.assertEligible(character, "Antica", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Free Account");
    }

    @Test
    void permiteFreeAccountNoAllowlistIgnorandoCaseEEspacos() {
        Character character = character("Meu Char");
        when(lookup.fetch(eq("Meu Char"))).thenReturn(freeAccount("Meu Char"));

        // Allowlist com case/espaços diferentes deve casar mesmo assim.
        TeamEligibilityServiceImpl service = serviceWithBypass(List.of("  meu char  "));

        TibiaCharacterSnapshot result = service.assertEligible(character, "Antica", null);

        assertThat(result.name()).isEqualTo("Meu Char");
    }

    @Test
    void allowlistNaoAfetaOutrasRegrasComoWorld() {
        Character character = character("Meu Char");
        when(lookup.fetch(any())).thenReturn(freeAccount("Meu Char"));

        TeamEligibilityServiceImpl service = serviceWithBypass(List.of("Meu Char"));

        // Mesmo isento de Premium, o world ainda tem que bater.
        assertThatThrownBy(() -> service.assertEligible(character, "Belobra", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("world");
    }
}
