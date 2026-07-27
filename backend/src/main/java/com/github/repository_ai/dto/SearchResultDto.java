package com.github.repository_ai.dto;

public record SearchResultDto(
        String chunkId,
        String filePath,
        String fileName,
        String language,
        Integer startLine,
        Integer endLine,
        String content,
        Double score
) {}
