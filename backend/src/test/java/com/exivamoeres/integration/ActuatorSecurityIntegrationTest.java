package com.exivamoeres.integration;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configuração padrão do Actuator: só o health responde, e ele responde para
 * qualquer um (é o healthcheck do Railway e do docker-compose).
 *
 * O teste que importa aqui é o do usuário logado: era exatamente esse o furo —
 * `/actuator/**` caía no `anyRequest().authenticated()` da API, então uma conta
 * qualquer lia as métricas internas.
 */
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

    @Test
    void healthEhPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthNaoDetalhaComponentes() throws Exception {
        // show-details: never — status do banco e do disco não são dado público.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void metricsSemCredencialRetorna401() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusSemCredencialRetorna401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioLogadoNaoLeMetricas() throws Exception {
        String token = jwtService.generateAccessToken(criarUsuario("actuator-user@teste.com"));

        // Ser usuário do produto não dá acesso a métrica nenhuma: a cadeia do
        // Actuator não conhece JWT, só o token dedicado.
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDoActuatorNaoConfiguradoNaoAbreNada() throws Exception {
        // Padrão de produção: ACTUATOR_TOKEN vazio. Mandar qualquer coisa no
        // header não pode virar acesso (o "vazio bate com vazio" clássico).
        mockMvc.perform(get("/actuator/metrics").header("X-Actuator-Token", ""))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").header("X-Actuator-Token", "qualquer-coisa"))
                .andExpect(status().isUnauthorized());
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
