package com.exivamoeres.repository;

import com.exivamoeres.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findAllByListIdOrderBySentAtDesc(Long listId, Pageable pageable);
}
