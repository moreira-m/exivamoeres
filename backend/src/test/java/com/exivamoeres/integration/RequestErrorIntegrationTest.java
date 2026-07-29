package com.exivamoeres.integration;

import com.exivamoeres.controller.ApiExceptionHandler;
import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.error.ApiErrorResponse;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requisição malformada é erro do <b>cliente</b> (4xx), não do servidor.
 *
 * Todos os casos abaixo respondiam <b>500 "Erro interno inesperado"</b>: o
 * `@ExceptionHandler(Exception.class)` engolia as exceções que o Spring lança
 * <i>antes</i> de o controller rodar. Duas consequências, e a segunda é a que
 * machuca: mentia sobre a culpa, e envenenava o alerta de taxa de 5xx do
 * {@code ops/observabilidade/alertas.yml} — um crawler passando `?page=abc` virava
 * incidente.
 *
 * A conversão de parâmetro é <b>por tipo</b>, então há um teste por tipo: cobrir só
 * o enum não prova nada sobre número nem sobre booleano.
 */
@AutoConfigureMockMvc
class RequestErrorIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ApiExceptionHandler handler;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

    /** Um usuário qualquer: o teste de rota inexistente precisa passar do Security. */
    private String tokenDeUmUsuarioQualquer() {
        User usuario = new User();
        usuario.setDisplayName("Sonda de rota");
        usuario.setAuthProvider(AuthProvider.ANONYMOUS);
        return jwtService.generateAccessToken(userRepository.save(usuario));
    }

    // ----- Conversão de parâmetro de query, um teste por tipo -----

    @Test
    void enumInvalidoRetorna400ListandoOsValoresAceitos() throws Exception {
        mockMvc.perform(get("/api/lists/search").param("vocation", "BANANA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parâmetro inválido"))
                // A lista de vocações é a informação mais útil que cabe aqui — e já é
                // pública: o próprio frontend as envia.
                .andExpect(jsonPath("$.fieldErrors.vocation").value(
                        org.hamcrest.Matchers.containsString("KNIGHT")));
    }

    @Test
    void numeroInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/lists/search").param("creatureId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.creatureId").value("deve ser um número"));
    }

    @Test
    void booleanoInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/lists/search").param("hasOpenSlots", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hasOpenSlots").value("deve ser true ou false"));
    }

    @Test
    void paginaInvalidaRetorna400() throws Exception {
        // O `page` vem do Pageable do Spring Data, não de um @RequestParam nosso —
        // e cai na mesma exceção. Está aqui porque é o parâmetro mais fácil de um
        // crawler estragar.
        mockMvc.perform(get("/api/lists/search").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("deve ser um número"));
    }

    @Test
    void pathVariableInvalidoRetorna400() throws Exception {
        // `/teams/abc` no navegador virava `/api/lists/NaN`: 500 depois de ~20s de
        // tentativas do React Query. O frontend agora barra antes (T3), mas quem
        // chama a API direto continuava recebendo 500.
        mockMvc.perform(get("/api/lists/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.id").value("deve ser um número"));
    }

    // ----- Corpo e Content-Type -----

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{isso nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Corpo da requisição ausente ou mal formado (esperado JSON)."));
    }

    @Test
    void corpoAusenteRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contentTypeNaoSuportadoRetorna415() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Esta API aceita apenas application/json."));
    }

    // ----- Caminho inexistente -----

    @Test
    void caminhoInexistenteAutenticadoRetorna404() throws Exception {
        // Era o 500 mais fácil de provocar da API: qualquer crawler pedindo
        // /api/qualquer-coisa. Sem token o Security responde 401 antes — e é por isso
        // que este teste manda um JWT válido.
        mockMvc.perform(get("/api/rota-que-nao-existe")
                        .header("Authorization", "Bearer " + tokenDeUmUsuarioQualquer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Este endereço não existe nesta API."));
    }

    @Test
    void caminhoInexistenteSemTokenContinua401() throws Exception {
        // Decisão preservada: para quem não está logado, a API não confirma quais
        // caminhos existem.
        mockMvc.perform(get("/api/rota-que-nao-existe"))
                .andExpect(status().isUnauthorized());
    }

    // ----- O que não vaza -----

    @Test
    void aRespostaNaoVazaNomeDeClasseNemAssinaturaDeMetodo() throws Exception {
        // A mensagem original do Spring é
        // "Required request body is missing: public ...AuthController.login(...)".
        // Devolver isso entrega o mapa interno da aplicação de graça.
        String corpo = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .doesNotContain("com.exivamoeres")
                .doesNotContain("Exception")
                .doesNotContain("AuthController");
    }

    @Test
    void parametroObrigatorioAusenteRetorna400() {
        // Nenhum endpoint tem `@RequestParam` obrigatório hoje, então não há como
        // provocar isto por HTTP — o handler é testado direto. É rede para o próximo
        // endpoint que tiver: sem ele, a ausência do parâmetro volta a ser 500.
        ApiErrorResponse resposta = handler.handleMissingParameter(
                new MissingServletRequestParameterException("world", "String"));

        assertThat(resposta.status()).isEqualTo(400);
        assertThat(resposta.fieldErrors()).containsEntry("world", "é obrigatório");
    }
}
