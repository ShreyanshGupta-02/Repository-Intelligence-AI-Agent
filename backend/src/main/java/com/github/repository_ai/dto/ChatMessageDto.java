package com.github.repository_ai.dto;

import com.github.repository_ai.entity.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt
) {}
