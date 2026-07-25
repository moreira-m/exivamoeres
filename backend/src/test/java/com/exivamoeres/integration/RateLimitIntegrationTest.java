package com.exivamoeres.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limit por IP dos endpoints que criam identidade (RateLimitFilter).
 *
 * Cada teste usa um IP próprio no X-Forwarded-For: os buckets são em memória e
 * vivem enquanto o contexto Spring vive, então IPs distintos são o que mantém um
 * teste (e as outras classes, que não mandam o header) fora do caminho do outro.
 */
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;

    @Test
    void quartaContaAnonimaDoMesmoIpNaMesmaHoraRetorna429() throws Exception {
        String ip = "203.0.113.10";
        // Limite padrão: 3/hora por IP (app.rate-limit.anonymous-per-hour).
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(anonymousLogin(ip)).andExpect(status().isCreated());
        }

        mockMvc.perform(anonymousLogin(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void limiteDeContaAnonimaEhPorIp() throws Exception {
        String esgotado = "203.0.113.20";
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(anonymousLogin(esgotado)).andExpect(status().isCreated());
        }
        mockMvc.perform(anonymousLogin(esgotado)).andExpect(status().isTooManyRequests());

        // Outro IP não paga pelo abuso do primeiro.
        mockMvc.perform(anonymousLogin("203.0.113.21")).andExpect(status().isCreated());
    }

    @Test
    void loginAcimaDeDezPorMinutoNoMesmoIpRetorna429() throws Exception {
        String ip = "203.0.113.30";
        // Credencial errada de propósito: o que se mede é a tentativa, não o acerto.
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(login(ip)).andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(login(ip)).andExpect(status().isTooManyRequests());
    }

    @Test
    void estourarOLimiteDeLoginNaoConsomeODeContaAnonima() throws Exception {
        String ip = "203.0.113.40";
        for (int i = 1; i <= 11; i++) {
            mockMvc.perform(login(ip));
        }

        // Buckets são por caminho: o /anonymous deste IP continua intacto.
        mockMvc.perform(anonymousLogin(ip)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder anonymousLogin(String ip) {
        return post("/api/auth/anonymous")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Visitante"}
                        """);
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"ninguem@teste.com","password":"senha-que-nao-existe"}
                        """);
    }
}
