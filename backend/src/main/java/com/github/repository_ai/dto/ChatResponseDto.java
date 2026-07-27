package com.github.repository_ai.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponseDto(
        UUID sessionId,
        String question,
        String answer,
        List<SearchResultDto> sources
) {}
