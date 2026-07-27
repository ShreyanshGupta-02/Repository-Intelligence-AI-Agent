package com.github.repository_ai.dto;

/**
 * Response DTO for the health check endpoint.
 */
public record HealthResponse(
        String status,
        String application,
        String timestamp
) {
}
