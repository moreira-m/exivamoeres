package com.exivamoeres.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registro -> login -> acesso a endpoint protegido, via HTTP (MockMvc). */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void fluxoCompletoDeRegistroLoginEAcessoProtegido() throws Exception {
        String email = "sir.exiva+" + System.nanoTime() + "@teste.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-segura-123","displayName":"Sir Exiva"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-segura-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = login.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/claims")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void endpointProtegidoSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaErradaRetornaMensagemGenerica() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"outro@teste.com","password":"senha-segura-123","displayName":"Outro"}
                                """))
                .andExpect(status().isCreated());

        // Mesma mensagem para senha errada e email inexistente:
        // não permitir enumeração de contas.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"outro@teste.com","password":"senha-errada"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Email ou senha incorretos"));
    }

    @Test
    void registroComDadosInvalidosRetorna400ComErrosDeCampo() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nao-e-email","password":"curta","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginAnonimoCriaContaEDevolveTokens() throws Exception {
        mockMvc.perform(post("/api/auth/anonymous")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Visitante"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.anonymous").value(true));
    }
}
