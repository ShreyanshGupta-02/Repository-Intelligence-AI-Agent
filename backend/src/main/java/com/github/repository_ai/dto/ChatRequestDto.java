package com.github.repository_ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatRequestDto(
        @NotBlank(message = "Question cannot be empty")
        String question,
        UUID sessionId,
        Integer topK
) {}
