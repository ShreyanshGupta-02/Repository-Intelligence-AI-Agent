package com.github.repository_ai.repository;

import com.github.repository_ai.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatMessage} entities.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findAllByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);

    void deleteAllByChatSessionId(UUID chatSessionId);
}
