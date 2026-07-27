package com.github.repository_ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new repository to analyze.
 */
public record CreateRepositoryRequest(

        @NotBlank(message = "GitHub URL is required")
        @Pattern(
                regexp = "^https://github\\.com/[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(/?)$",
                message = "Must be a valid GitHub repository URL (e.g. https://github.com/owner/repo)"
        )
        String githubUrl
) {
}
