package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.TooManyRequestsException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.service.CharacterClaimService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rate limit por usuário das ações caras (UserRateLimiter): criar time e
 * consultar a TibiaData.
 *
 * Os limites reais (10 e 20 por hora) são generosos de propósito para um humano;
 * aqui eles são baixados para 2 para o teste exercitar a regra sem criar dezenas
 * de times. O que se verifica é o comportamento no estouro, não o número.
 */
class UserRateLimitIntegrationTest extends TeamIntegrationTestBase {

    @DynamicPropertySource
    static void limitesBaixos(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.team-creation-per-hour", () -> 2);
        registry.add("app.rate-limit.tibiadata-per-hour", () -> 2);
    }

    @Autowired HuntingListService listService;
    @Autowired CharacterClaimService claimService;

    @Test
    void criarTimesAcimaDoLimitePorHoraRetorna429() {
        User owner = createUser("rate-times@teste.com");
        criarTime(owner, "Rate Char 1", "Rate Team 1");
        criarTime(owner, "Rate Char 2", "Rate Team 2");

        // O plano free ainda permitiria o 3º time ativo — quem barra é o limite
        // de criações por hora, que é o que impede o loop criar-encerrar-criar.
        assertThatThrownBy(() -> criarTime(owner, "Rate Char 3", "Rate Team 3"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("muitos times");
    }

    @Test
    void oLimiteDeCriacaoEhPorUsuario() {
        User esgotado = createUser("rate-esgotado@teste.com");
        criarTime(esgotado, "Esg Char 1", "Esg Team 1");
        criarTime(esgotado, "Esg Char 2", "Esg Team 2");
        assertThatThrownBy(() -> criarTime(esgotado, "Esg Char 3", "Esg Team 3"))
                .isInstanceOf(TooManyRequestsException.class);

        User outro = createUser("rate-outro@teste.com");
        assertThatCode(() -> criarTime(outro, "Outro Char", "Outro Team"))
                .doesNotThrowAnyException();
    }

    @Test
    void claimsAcimaDoLimitePorHoraRetornam429() {
        User claimant = createUser("rate-claims@teste.com");
        stubAnyPremium();
        claimService.startClaim(claimant.getId(), "Rate Claim Um");
        claimService.startClaim(claimant.getId(), "Rate Claim Dois");

        assertThatThrownBy(() -> claimService.startClaim(claimant.getId(), "Rate Claim Tres"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Tibia.com");
    }

    private void criarTime(User owner, String characterName, String teamName) {
        Character character = createCharacter(characterName, "Antica", owner);
        stubPremium(characterName, "Antica");
        listService.createList(owner.getId(), new CreateListRequest(
                teamName, "Antica", creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT,
                character.getId(), null, null));
    }
}
