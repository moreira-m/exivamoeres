package com.exivamoeres.integration;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cenário de scraping: `ACTUATOR_ENDPOINTS` liga metrics/prometheus e
 * `ACTUATOR_TOKEN` define quem pode ler.
 *
 * Este contexto é o que garante que a segunda camada de defesa é real. No
 * contexto padrão ({@link ActuatorSecurityIntegrationTest}) os endpoints nem
 * existem, então um 401 lá poderia ser só o 404 disfarçado pela cadeia de
 * segurança — aqui eles existem e respondem 200 com o token certo.
 */
@AutoConfigureMockMvc
// Sem isto o Spring Boot desliga a exportação de métricas em teste
// (`management.defaults.metrics.export.enabled=false`) e o endpoint
// /actuator/prometheus nem existe — 404 em vez do 200 que se quer provar.
@AutoConfigureObservability(tracing = false)
class ActuatorTokenIntegrationTest extends IntegrationTestBase {

    private static final String TOKEN = "token-de-teste-do-actuator";

    @DynamicPropertySource
    static void actuatorAberto(DynamicPropertyRegistry registry) {
        registry.add("management.endpoints.web.exposure.include",
                () -> "health,metrics,prometheus");
        registry.add("app.actuator.token", () -> TOKEN);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

    @Test
    void metricsComOTokenDedicadoRetorna200() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("X-Actuator-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void prometheusComOTokenDedicadoRetorna200() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("X-Actuator-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void tokenErradoRetorna401() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header("X-Actuator-Token", "token-errado"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioLogadoContinuaSemAcessoMesmoComOsEndpointsExpostos() throws Exception {
        String jwt = jwtService.generateAccessToken(criarUsuario("actuator-token@teste.com"));

        // A regressão do S5 na sua forma mais forte: endpoints ligados, usuário
        // autenticado de verdade, e ainda assim 401.
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthSegueEmPeSemToken() throws Exception {
        // Trancar as métricas não pode derrubar o healthcheck do deploy.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private User criarUsuario(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Curioso");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante");
        return userRepository.save(user);
    }
}
