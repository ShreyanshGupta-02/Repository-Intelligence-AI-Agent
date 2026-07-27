package com.github.repository_ai.dto;

import java.util.UUID;

public record ArchitectureSummaryDto(
        UUID repositoryId,
        String repositoryName,
        String summary,
        boolean isCached
) {}
