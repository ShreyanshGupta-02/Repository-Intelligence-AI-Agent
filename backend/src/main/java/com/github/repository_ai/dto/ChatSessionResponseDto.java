package com.github.repository_ai.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponseDto(
        UUID id,
        String title,
        UUID repositoryId,
        Instant createdAt
) {}
