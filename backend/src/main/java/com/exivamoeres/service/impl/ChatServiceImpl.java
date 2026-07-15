package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.ChatMessage;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.chat.ChatMessageResponse;
import com.exivamoeres.repository.ChatMessageRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    private static final String TOPIC_TEMPLATE = "/topic/lists/%d/chat";

    private final ChatMessageRepository chatMessageRepository;
    private final HuntingListRepository listRepository;
    private final UserRepository userRepository;
    private final TeamMembershipGuard membershipGuard;
    private final ChatRateLimiter rateLimiter;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatServiceImpl(ChatMessageRepository chatMessageRepository,
                           HuntingListRepository listRepository,
                           UserRepository userRepository,
                           TeamMembershipGuard membershipGuard,
                           ChatRateLimiter rateLimiter,
                           SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.listRepository = listRepository;
        this.userRepository = userRepository;
        this.membershipGuard = membershipGuard;
        this.rateLimiter = rateLimiter;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long userId, Long listId, Long characterId, String content) {
        // Autorização: personagem próprio + membro ativo/aprovado do time.
        Character character = membershipGuard.requireActiveMember(userId, listId, characterId);
        if (!rateLimiter.tryConsume(userId)) {
            throw new BusinessRuleException("Você está enviando mensagens rápido demais; aguarde um pouco");
        }

        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        ChatMessage message = new ChatMessage();
        message.setList(list);
        message.setSender(sender);
        message.setCharacter(character);
        message.setContent(content);
        chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        messagingTemplate.convertAndSend(TOPIC_TEMPLATE.formatted(listId), response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getHistory(Long userId, Long listId, Pageable pageable) {
        membershipGuard.requireActiveMember(userId, listId);
        return chatMessageRepository
                .findAllByListIdOrderBySentAtDesc(listId, pageable)
                .map(ChatMessageResponse::from);
    }
}
