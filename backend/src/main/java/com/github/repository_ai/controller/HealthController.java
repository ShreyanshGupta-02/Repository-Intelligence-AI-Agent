package com.github.repository_ai.controller;

import com.github.repository_ai.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Health check endpoint to verify the backend is running and reachable.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = new HealthResponse(
                "UP",
                "Repository Intelligence AI Agent",
                Instant.now().toString()
        );
        return ResponseEntity.ok(response);
    }
}
