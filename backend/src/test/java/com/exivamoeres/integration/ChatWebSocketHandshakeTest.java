package com.exivamoeres.integration;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.chat.ChatMessageResponse;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.security.JwtService;
import com.exivamoeres.service.ChatService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Handshake do chat via STOMP num servidor real (RANDOM_PORT). Valida que o
 * CONNECT exige JWT válido e que mensagens chegam aos inscritos no tópico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketHandshakeTest extends IntegrationTestBase {

    @LocalServerPort int port;

    @MockBean TibiaDataClient tibiaDataClient;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired CharacterRepository characterRepository;
    @Autowired HuntingListService listService;
    @Autowired ChatService chatService;

    @Test
    void conexaoSemTokenEhRejeitada() {
        WebSocketStompClient client = newStompClient();
        StompHeaders connectHeaders = new StompHeaders(); // sem Authorization

        // Handshake passa (Origin permitido); o interceptor é que rejeita o
        // CONNECT sem JWT — a conexão nunca completa.
        assertThatThrownBy(() ->
                client.connectAsync(wsUrl(), allowedOriginHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    void membroComTokenRecebeBroadcast() throws Exception {
        when(tibiaDataClient.fetchCharacter(anyString())).thenAnswer(inv ->
                Mono.just(new TibiaCharacterSnapshot(true, inv.getArgument(0), "Antica", "",
                        "Premium Account", "Elder Druid")));

        User owner = newUser("ws-owner@teste.com");
        Character ownerChar = newCharacter("WS Owner", owner);
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time WS", "Antica",
                creatureId(), JoinPolicy.AUTO_ACCEPT, ownerChar.getId()));

        String token = jwtService.generateAccessToken(owner);
        WebSocketStompClient client = newStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = client
                .connectAsync(wsUrl(), allowedOriginHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<String> received = new CompletableFuture<>();
        session.subscribe("/topic/lists/" + team.summary().id() + "/chat", new StompFrameHandler() {
            @Override
            public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
                return ChatMessageResponse.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.complete(((ChatMessageResponse) payload).content());
            }
        });

        // Dá tempo do SUBSCRIBE assentar no broker antes de publicar.
        Thread.sleep(500);
        // Envia via serviço (que persiste e faz broadcast no tópico).
        chatService.sendMessage(owner.getId(), team.summary().id(), ownerChar.getId(), "olá pelo ws");

        assertThat(received.get(5, TimeUnit.SECONDS)).isEqualTo("olá pelo ws");
        session.disconnect();
    }

    // ----- Helpers -----

    private WebSocketStompClient newStompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // JavaTimeModule: o payload tem Instant (sentAt) serializado como ISO-8601.
        converter.setObjectMapper(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        client.setMessageConverter(converter);
        return client;
    }

    /** Origin igual ao app.cors.allowed-origin (default de teste) — o endpoint /ws restringe origem. */
    private org.springframework.web.socket.WebSocketHttpHeaders allowedOriginHeaders() {
        var headers = new org.springframework.web.socket.WebSocketHttpHeaders();
        headers.add("Origin", "http://localhost:5173");
        return headers;
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private Long creatureId() {
        return creatureRepository().findByNameIgnoreCase("Demon").orElseThrow().getId();
    }

    private com.exivamoeres.repository.CreatureRepository creatureRepository() {
        return creatureRepo;
    }

    @Autowired com.exivamoeres.repository.CreatureRepository creatureRepo;

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("WS " + email);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash");
        return userRepository.save(user);
    }

    private Character newCharacter(String name, User owner) {
        Character character = new Character();
        character.setName(name);
        character.setWorld("Antica");
        character.setVocation("Elder Druid");
        character.setOwner(owner);
        return characterRepository.save(character);
    }
}
