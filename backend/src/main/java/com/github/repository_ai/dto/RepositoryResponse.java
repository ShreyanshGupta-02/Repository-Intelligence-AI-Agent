package com.github.repository_ai.dto;

import com.github.repository_ai.entity.RepositoryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for repository data — never exposes JPA entities directly.
 */
public record RepositoryResponse(
        UUID id,
        String githubUrl,
        String name,
        RepositoryStatus status,
        String errorMessage,
        String architectureSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
