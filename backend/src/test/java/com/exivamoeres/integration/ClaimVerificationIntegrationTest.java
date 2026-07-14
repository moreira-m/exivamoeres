package com.exivamoeres.integration;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.CharacterClaim;
import com.exivamoeres.domain.ClaimStatus;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.CharacterClaimRepository;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.ClaimVerificationService;
import com.exivamoeres.service.ClaimVerificationService.VerificationOutcome;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do fluxo crítico de verificação de claim, ponta a ponta:
 * Postgres real (Testcontainers) + TibiaData simulada (WireMock).
 */
class ClaimVerificationIntegrationTest extends IntegrationTestBase {

    // Nome com espaço de propósito: valida o URL-encoding na chamada HTTP.
    private static final String CHARACTER_NAME = "Sir Exiva";
    private static final String ENCODED_PATH = "/v4/character/Sir%20Exiva";
    private static final String CODE = "EXIVA-ABC12345";

    static final WireMockServer TIBIADATA = new WireMockServer(
            WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        TIBIADATA.start();
    }

    @DynamicPropertySource
    static void tibiaDataProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tibiadata.base-url", TIBIADATA::baseUrl);
        // Backoff curto nos testes — em produção é exponencial a partir de 2s.
        registry.add("resilience4j.retry.instances.tibiadata.wait-duration", () -> "50ms");
        registry.add("resilience4j.retry.instances.tibiadata.enable-exponential-backoff", () -> "false");
    }

    @AfterAll
    static void stopWireMock() {
        TIBIADATA.stop();
    }

    @Autowired ClaimVerificationService verificationService;
    @Autowired CharacterClaimRepository claimRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired UserRepository userRepository;
    @Autowired HuntingListRepository huntingListRepository;
    @Autowired ListMembershipRepository membershipRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        TIBIADATA.resetAll();
        membershipRepository.deleteAll();
        huntingListRepository.deleteAll();
        claimRepository.deleteAll();
        characterRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----- Casos obrigatórios de matching do comment -----

    @Test
    void aprovaQuandoCommentTemCodigoComEspacosExtras() {
        CharacterClaim claim = pendingClaim();
        stubCharacterWithComment("   " + CODE + "   ");

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.APPROVED);
        assertApproved(claim);
    }

    @Test
    void aprovaQuandoCommentTemCodigoEntreQuebrasDeLinha() {
        CharacterClaim claim = pendingClaim();
        stubCharacterWithComment("Main do time\\n" + CODE + "\\nprocurando cores de dragon");

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.APPROVED);
        assertApproved(claim);
    }

    @Test
    void aprovaQuandoCommentTemCodigoEmOutroCase() {
        CharacterClaim claim = pendingClaim();
        stubCharacterWithComment(CODE.toLowerCase());

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.APPROVED);
        assertApproved(claim);
    }

    @Test
    void naoAprovaQuandoCommentEstaVazio() {
        CharacterClaim claim = pendingClaim();
        stubCharacterWithComment("");

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.CODE_NOT_FOUND);
        CharacterClaim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.PENDING);
        // Resposta válida da API conta como checagem, mesmo sem match.
        assertThat(reloaded.getLastCheckedAt()).isNotNull();
    }

    // ----- Aprovação atômica e transferência de posse -----

    @Test
    void aprovacaoTransferePosseEDesativaMembershipsDoDonoAnterior() {
        User previousOwner = newUser("dono-antigo@teste.com");
        User claimant = newUser("novo-dono@teste.com");
        Character character = newCharacter(previousOwner);
        ListMembership membership = membershipInList(previousOwner, character);
        CharacterClaim claim = newClaim(character, claimant);
        stubCharacterWithComment(CODE);

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.APPROVED);
        Character reloadedCharacter = characterRepository.findById(character.getId()).orElseThrow();
        assertThat(reloadedCharacter.getOwner().getId()).isEqualTo(claimant.getId());
        ListMembership reloadedMembership =
                membershipRepository.findById(membership.getId()).orElseThrow();
        assertThat(reloadedMembership.isActive()).isFalse();
    }

    // ----- Expiração -----

    @Test
    void rejeitaClaimPendenteHaMaisDe24Horas() {
        CharacterClaim claim = pendingClaim();
        // created_at é imutável pela JPA (@PrePersist); envelhece direto no banco.
        jdbcTemplate.update(
                "UPDATE character_claims SET created_at = now() - INTERVAL '25 hours' WHERE id = ?",
                claim.getId());

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.EXPIRED);
        CharacterClaim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(reloaded.getResolvedAt()).isNotNull();
        // Expirou antes de consultar a API — nenhuma chamada deve ter saído.
        TIBIADATA.verify(0, getRequestedFor(urlEqualTo(ENCODED_PATH)));
    }

    // ----- Falha de rede: retry sem corromper o claim -----

    @Test
    void falhaDeRedeFazRetryENaoAlteraOClaim() {
        CharacterClaim claim = pendingClaim();
        TIBIADATA.stubFor(get(urlEqualTo(ENCODED_PATH))
                .willReturn(aResponse().withStatus(500)));

        VerificationOutcome outcome = verificationService.verifyClaim(claim.getId());

        assertThat(outcome).isEqualTo(VerificationOutcome.UNREACHABLE);
        CharacterClaim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.PENDING);
        // Falha de rede NÃO conta como checagem.
        assertThat(reloaded.getLastCheckedAt()).isNull();
        // Resilience4j: 1 tentativa original + 2 retries.
        TIBIADATA.verify(3, getRequestedFor(urlEqualTo(ENCODED_PATH)));
    }

    // ----- Helpers -----

    private CharacterClaim pendingClaim() {
        User claimant = newUser("claimant@teste.com");
        Character character = newCharacter(null);
        return newClaim(character, claimant);
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Jogador " + email);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante-para-o-teste");
        return userRepository.save(user);
    }

    private Character newCharacter(User owner) {
        Character character = new Character();
        character.setName(CHARACTER_NAME);
        character.setWorld("Antica");
        character.setOwner(owner);
        return characterRepository.save(character);
    }

    private CharacterClaim newClaim(Character character, User claimant) {
        CharacterClaim claim = new CharacterClaim();
        claim.setCharacter(character);
        claim.setClaimant(claimant);
        claim.setVerificationCode(CODE);
        claim.setStatus(ClaimStatus.PENDING);
        return claimRepository.save(claim);
    }

    private ListMembership membershipInList(User owner, Character character) {
        HuntingList list = new HuntingList();
        list.setName("Time do Dragon");
        list.setWorld("Antica");
        list.setShareCode("SHARE123");
        list.setOwner(owner);
        huntingListRepository.save(list);

        ListMembership membership = new ListMembership();
        membership.setList(list);
        membership.setUser(owner);
        membership.setCharacter(character);
        membership.setActive(true);
        return membershipRepository.save(membership);
    }

    /** Stub da TibiaData v4; o comment entra cru no JSON (use \\n para quebra de linha). */
    private void stubCharacterWithComment(String comment) {
        String body = """
                {
                  "character": {
                    "character": {
                      "name": "%s",
                      "world": "Antica",
                      "comment": "%s"
                    }
                  }
                }
                """.formatted(CHARACTER_NAME, comment);
        TIBIADATA.stubFor(get(urlEqualTo(ENCODED_PATH))
                .willReturn(okJson(body)));
    }

    private void assertApproved(CharacterClaim claim) {
        CharacterClaim reloaded = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(reloaded.getResolvedAt()).isNotNull();
        assertThat(reloaded.getLastCheckedAt()).isNotNull();
        Character character = characterRepository.findById(reloaded.getCharacter().getId()).orElseThrow();
        assertThat(character.getOwner().getId()).isEqualTo(reloaded.getClaimant().getId());
    }
}
