package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.error.ErrorCode;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As recusas de regra carregam <b>código e valores</b>, não só a frase em português
 * (item T2).
 *
 * <p>Sem isso, quem usa o site em inglês recebia *"O time está cheio (máximo de 5
 * jogadores)"*. Traduzir no backend seria pior: o idioma do usuário não é assunto de regra
 * de negócio, e o `Accept-Language` não existe em job nem em log.</p>
 *
 * <p><b>A frase em português continua vindo</b> em todas elas — é a reserva que mantém a
 * migração incremental e o log legível. Estes testes afirmam as duas coisas: o código está
 * lá, e a frase também.</p>
 */
@AutoConfigureMockMvc
class ErrorCodeIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

    @Test
    void personagemDeOutroWorldTrazCodigoEOsTresValores() {
        Ctx ctx = timeCom("CodeWorldA", "CodeWorld", null);
        Long deOutroWorld = personagemDe("code-outro", "Code Outro", "CodeWorldB", 300, "Elder Druid");

        assertThatThrownBy(() -> entrar(ctx, deOutroWorld))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.WORLD_MISMATCH);
                    // Os três valores são o que a frase da tela precisa para ser útil:
                    // "X é do mundo Y, e o time é do mundo Z".
                    assertThat(e.getParams())
                            .containsEntry("character", "Code Outro")
                            .containsEntry("characterWorld", "CodeWorldB")
                            .containsEntry("teamWorld", "CodeWorldA");
                    // E a reserva em português continua lá.
                    assertThat(e.getMessage()).contains("é do world CodeWorldB");
                });
    }

    @Test
    void freeAccountTrazOCodigoEONomeDoPersonagem() {
        Ctx ctx = timeCom("CodeFreeWorld", "CodeFree", null);
        User dono = createUser("code-free-candidato@teste.com");
        Character free = createCharacter("Code Free Cand", "CodeFreeWorld", dono);
        stubFreeAccount("Code Free Cand", "CodeFreeWorld");

        assertThatThrownBy(() -> listService.joinByShareCode(
                dono.getId(), ctx.shareCode, new JoinListRequest(free.getId())))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.FREE_ACCOUNT);
                    assertThat(e.getParams()).containsEntry("character", "Code Free Cand");
                });
    }

    @Test
    void levelAbaixoDoMinimoTrazRequisitoELevel() {
        Ctx ctx = timeCom("CodeLevelWorld", "CodeLevel", 400);
        Long baixo = personagemDe("code-baixo", "Code Baixo", "CodeLevelWorld", 150, "Elder Druid");

        assertThatThrownBy(() -> entrar(ctx, baixo))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.BELOW_MINIMUM_LEVEL);
                    assertThat(e.getParams())
                            .containsEntry("minimum", "400")
                            .containsEntry("level", "150")
                            .containsEntry("character", "Code Baixo");
                });
    }

    @Test
    void levelDesconhecidoViraInterrogacaoEmVezDeNull() {
        Ctx ctx = timeCom("CodeUnknownWorld", "CodeUnknown", 400);
        User dono = createUser("code-sem-level@teste.com");
        Character semLevel = createCharacter("Code Sem Level", "CodeUnknownWorld", dono);
        stubPremium("Code Sem Level", "CodeUnknownWorld", 0, "Elder Druid");
        // A TibiaData às vezes não traz o level; o snapshot vem com ele nulo.
        stubSemLevel("Code Sem Level", "CodeUnknownWorld");

        assertThatThrownBy(() -> listService.joinByShareCode(
                dono.getId(), ctx.shareCode, new JoinListRequest(semLevel.getId())))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    // `null` no mapa viraria "level null" na tela; "?" é o que ela mostra.
                    assertThat(e.getParams()).containsEntry("level", "?");
                    assertThat(e.getMessage()).contains("desconhecido");
                });
    }

    @Test
    void timeCheioTrazOMaximo() {
        // O time nasce com o dono dentro; enche até o limite de produção (5).
        Ctx ctx = timeCom("CodeFullWorld", "CodeFull", null);
        for (int i = 1; i <= 4; i++) {
            entrar(ctx, personagemDe("code-full-" + i, "Code Full " + i, "CodeFullWorld", 300, "Elder Druid"));
        }
        Long sobrando = personagemDe("code-full-x", "Code Full X", "CodeFullWorld", 300, "Elder Druid");

        assertThatThrownBy(() -> entrar(ctx, sobrando))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.TEAM_FULL);
                    // O número vem do `app.team.max-members`: a frase da tela não o crava.
                    assertThat(e.getParams()).containsEntry("max", "5");
                });
    }

    @Test
    void pedidoRepetidoEMembroRepetidoTemCodigosDiferentes() {
        Ctx manual = timeCom("CodeDupWorld", "CodeDup", null, JoinPolicy.MANUAL_APPROVAL);
        Long candidato = personagemDe("code-dup", "Code Dup", "CodeDupWorld", 300, "Elder Druid");
        entrar(manual, candidato); // fica PENDING

        // Dois estados diferentes, duas frases diferentes: "já pediu" ≠ "já está dentro".
        assertThatThrownBy(() -> entrar(manual, candidato))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PENDING_REQUEST_EXISTS));

        Ctx automatico = timeCom("CodeDup2World", "CodeDup2", null);
        Long membro = personagemDe("code-dup2", "Code Dup2", "CodeDup2World", 300, "Elder Druid");
        entrar(automatico, membro); // entra APPROVED

        assertThatThrownBy(() -> entrar(automatico, membro))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ALREADY_MEMBER));
    }

    @Test
    void composicaoSemVagaTrazAVocacao() {
        Ctx ctx = timeCom("CodeSlotWorld", "CodeSlot", null, JoinPolicy.AUTO_ACCEPT,
                List.of(Vocation.DRUID, Vocation.DRUID));
        Long knight = personagemDe("code-slot", "Code Slot", "CodeSlotWorld", 300, "Elite Knight");

        assertThatThrownBy(() -> entrar(ctx, knight))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VOCATION_WITHOUT_SLOT);
                    assertThat(e.getParams()).containsEntry("vocation", "KNIGHT");
                });
    }

    @Test
    void recusaAindaNaoConvertidaContinuaFuncionandoSemCodigo() {
        Ctx ctx = timeCom("CodeLegacyWorld", "CodeLegacy", null);

        // "O dono não pode sair do próprio time" ainda não tem código — e é exatamente o
        // que a migração incremental do T2 permite: a frase em português continua chegando.
        assertThatThrownBy(() -> listService.leaveList(ctx.ownerId, ctx.listId))
                .asInstanceOf(type(BusinessRuleException.class))
                .satisfies(e -> {
                    assertThat(e.getCode()).isNull();
                    assertThat(e.getParams()).isEmpty();
                    assertThat(e.getMessage()).isNotBlank();
                });
    }

    @Test
    void oEnvelopeHttpCarregaCodigoEParams() throws Exception {
        // O teste que faltava: os outros afirmam sobre a **exceção**, e o que o frontend lê
        // é o **JSON**. Sem este, o handler poderia descartar código e params e nada
        // reprovaria (a mutação passou até este caso existir).
        Ctx ctx = timeCom("CodeHttpWorld", "CodeHttp", null);
        User quemPede = createUser("code-http-cand@teste.com");
        Character free = createCharacter("Code Http Cand", "CodeHttpWorld", quemPede);
        stubFreeAccount("Code Http Cand", "CodeHttpWorld");

        mockMvc.perform(post("/api/lists/{shareCode}/join", ctx.shareCode)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(quemPede))
                        .contentType(APPLICATION_JSON)
                        .content("{\"characterId\":" + free.getId() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FREE_ACCOUNT"))
                .andExpect(jsonPath("$.params.character").value("Code Http Cand"))
                // A reserva em português continua no corpo — é o que o log e as regras não
                // convertidas usam.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Free Account")));
    }

    @Test
    void oEnvelopeDeRecusaLegadaTrazCodigoNulo() throws Exception {
        Ctx ctx = timeCom("CodeHttpLegacyWorld", "CodeHttpLegacy", null);

        // Sair do próprio time: recusa ainda não convertida. O campo existe no shape e vem
        // nulo — é o que o frontend trata caindo no `message`.
        mockMvc.perform(post("/api/lists/{id}/leave", ctx.listId)
                        .header("Authorization", "Bearer " + tokenDoDono(ctx)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").exists());
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, String shareCode) {
    }

    private Ctx timeCom(String world, String prefixo, Integer minimo) {
        return timeCom(world, prefixo, minimo, JoinPolicy.AUTO_ACCEPT);
    }

    private Ctx timeCom(String world, String prefixo, Integer minimo, JoinPolicy politica) {
        return timeCom(world, prefixo, minimo, politica, null);
    }

    private Ctx timeCom(String world, String prefixo, Integer minimo, JoinPolicy politica,
                        List<Vocation> composicao) {
        User dono = createUser(prefixo.toLowerCase() + "-code-dono@teste.com");
        Character donoChar = createCharacter(prefixo + " Dono", world, dono);
        stubPremium(prefixo + " Dono", world, 500, "Elder Druid");
        ListDetailResponse time = listService.createList(dono.getId(), new CreateListRequest(
                prefixo + " Team", world, creature("Demon").getId(), politica, donoChar.getId(),
                minimo, null, null, null, null, composicao));
        return new Ctx(time.summary().id(), dono.getId(), time.summary().shareCode());
    }

    /** Cria usuário + personagem verificado e devolve o id do personagem. */
    private Long personagemDe(String email, String nome, String world, int level, String vocacao) {
        User user = createUser(email + "@teste.com");
        Character character = createCharacter(nome, world, user);
        stubPremium(nome, world, level, vocacao);
        donos.put(character.getId(), user.getId());
        return character.getId();
    }

    private final java.util.Map<Long, Long> donos = new java.util.HashMap<>();

    private void entrar(Ctx ctx, Long characterId) {
        listService.joinByShareCode(donos.get(characterId), ctx.shareCode,
                new JoinListRequest(characterId));
    }

    /** Token do dono do time — para as chamadas HTTP. */
    private String tokenDoDono(Ctx ctx) {
        return jwtService.generateAccessToken(userRepository.findById(ctx.ownerId).orElseThrow());
    }

    private void stubSemLevel(String nome, String world) {
        org.mockito.Mockito.when(tibiaDataClient.fetchCharacter(nome)).thenReturn(
                reactor.core.publisher.Mono.just(new com.exivamoeres.client.TibiaCharacterSnapshot(
                        true, nome, world, "", "Premium Account", "Elder Druid", null)));
    }
}
