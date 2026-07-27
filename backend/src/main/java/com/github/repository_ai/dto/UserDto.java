package com.github.repository_ai.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String email,
        Instant createdAt
) {}
