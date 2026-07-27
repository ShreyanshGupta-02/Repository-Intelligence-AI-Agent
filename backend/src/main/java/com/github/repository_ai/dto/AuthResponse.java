package com.github.repository_ai.dto;

public record AuthResponse(
        String token,
        UserDto user
) {}
