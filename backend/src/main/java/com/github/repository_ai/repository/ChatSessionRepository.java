package com.github.repository_ai.repository;

import com.github.repository_ai.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ChatSession} entities.
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findAllByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<ChatSession> findAllByRepositoryIdAndUserIdOrderByCreatedAtDesc(UUID repositoryId, UUID userId);

    void deleteAllByRepositoryId(UUID repositoryId);
}
