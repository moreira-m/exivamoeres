package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.chat.ChatMessageResponse;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.service.ChatService;
import com.exivamoeres.service.HuntingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chat a nível de serviço: autorização por membership e persistência vinculada a personagem. */
class ChatIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired ChatService chatService;

    @Test
    void membroEnviaEHistoricoRetornaMensagemComPersonagem() {
        User owner = createUser("chat-owner@teste.com");
        Character ownerChar = createCharacter("Chat Owner", "Antica", owner);
        stubPremium("Chat Owner", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time do Chat", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId()));

        ChatMessageResponse sent = chatService.sendMessage(
                owner.getId(), team.summary().id(), ownerChar.getId(), "bora caçar?");

        assertThat(sent.characterId()).isEqualTo(ownerChar.getId());
        assertThat(sent.characterName()).isEqualTo("Chat Owner");

        var history = chatService.getHistory(owner.getId(), team.summary().id(), PageRequest.of(0, 30));
        assertThat(history.getContent()).extracting(ChatMessageResponse::content).contains("bora caçar?");
    }

    @Test
    void naoMembroNaoEnviaMensagem() {
        User owner = createUser("chat-owner2@teste.com");
        Character ownerChar = createCharacter("Chat Owner Two", "Antica", owner);
        stubPremium("Chat Owner Two", "Antica");
        ListDetailResponse team = listService.createList(owner.getId(), new CreateListRequest(
                "Time Fechado", "Antica", creature("Demon").getId(),
                JoinPolicy.AUTO_ACCEPT, ownerChar.getId()));

        User stranger = createUser("chat-stranger@teste.com");
        Character strangerChar = createCharacter("Chat Stranger", "Antica", stranger);

        assertThatThrownBy(() -> chatService.sendMessage(
                stranger.getId(), team.summary().id(), strangerChar.getId(), "oi"))
                .hasMessageContaining("não é membro");
    }
}
