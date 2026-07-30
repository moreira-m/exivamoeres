package com.exivamoeres.integration;

import com.exivamoeres.security.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * O {@code X-Request-Id} tem que chegar ao <b>JavaScript</b>, não só à resposta.
 *
 * <p>O filtro do T6 já punha o id no cabeçalho, e o {@code RequestIdFilterTest} prova
 * isso. O que faltava é específico de navegador: em requisição de outra origem — e o
 * frontend está <b>sempre</b> em outra origem (Netlify × Railway, 5173 × 8080 no
 * desenvolvimento) — o navegador só entrega ao código os cabeçalhos listados em
 * {@code Access-Control-Expose-Headers}. Sem essa linha, `response.headers` chega
 * <b>sem</b> o id, e o fluxo que o T6 queria destravar ("deu erro, me manda o id") não
 * fecha: quem reporta não tem o que copiar.</p>
 *
 * <p>É um teste de <b>configuração</b>, e existe porque a falha é invisível de dentro:
 * o `curl` mostra o cabeçalho na resposta e tudo parece certo — quem esconde é o
 * navegador, depois.</p>
 */
@AutoConfigureMockMvc
class RequestIdVisibilityIntegrationTest extends IntegrationTestBase {

    /** A mesma origem do `app.cors.allowed-origin` de desenvolvimento. */
    private static final String ORIGEM = "http://localhost:5173";

    @Autowired MockMvc mockMvc;

    @Test
    void aRespostaExpoeORequestIdParaOutraOrigem() throws Exception {
        var resposta = mockMvc.perform(get("/api/lists/search")
                        .header(HttpHeaders.ORIGIN, ORIGEM))
                .andReturn().getResponse();

        assertThat(resposta.getHeader(RequestIdFilter.HEADER))
                .as("o id continua na resposta (T6)")
                .isNotBlank();
        assertThat(resposta.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .as("sem isto o navegador esconde o id do JavaScript")
                .contains(RequestIdFilter.HEADER);
    }

    @Test
    void oPreflightTambemAnunciaOCabecalhoExposto() throws Exception {
        // O preflight é o que o navegador consulta antes de um PATCH/DELETE: se a
        // resposta dele discordasse da resposta real, o comportamento variaria por
        // método — o tipo de defeito que só aparece numa tela específica.
        var resposta = mockMvc.perform(options("/api/lists/1")
                        .header(HttpHeaders.ORIGIN, ORIGEM)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andReturn().getResponse();

        assertThat(resposta.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains(RequestIdFilter.HEADER);
    }

    @Test
    void oIdExpostoEhOMesmoDaResposta() throws Exception {
        var resposta = mockMvc.perform(get("/api/lists/search")
                        .header(HttpHeaders.ORIGIN, ORIGEM))
                .andReturn().getResponse();

        // O que a tela vai mostrar tem que ser o que está no log do servidor — id
        // "de mentira" na tela manda quem investiga procurar o que não existe.
        String id = resposta.getHeader(RequestIdFilter.HEADER);
        assertThat(id).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void oErroTambemTrazOId() throws Exception {
        // É o caso que importa: id em resposta de sucesso não serve para reportar nada.
        var resposta = mockMvc.perform(get("/api/lists/999999")
                        .header(HttpHeaders.ORIGIN, ORIGEM))
                .andReturn().getResponse();

        assertThat(resposta.getStatus()).isEqualTo(404);
        assertThat(resposta.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(resposta.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains(RequestIdFilter.HEADER);
    }
}
