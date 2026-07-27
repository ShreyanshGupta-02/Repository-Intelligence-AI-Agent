package com.github.repository_ai.controller;

import com.github.repository_ai.dto.CreateRepositoryRequest;
import com.github.repository_ai.dto.RepositoryResponse;
import com.github.repository_ai.dto.SearchResultDto;
import com.github.repository_ai.service.RepositoryService;
import com.github.repository_ai.service.RetrievalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for repository CRUD operations and semantic search.
 * Controllers ONLY receive/return DTOs and delegate to services — no business logic here.
 */
@RestController
@RequestMapping("/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final RetrievalService retrievalService;
    private final com.github.repository_ai.service.SummaryService summaryService;

    public RepositoryController(RepositoryService repositoryService,
                              RetrievalService retrievalService,
                              com.github.repository_ai.service.SummaryService summaryService) {
        this.repositoryService = repositoryService;
        this.retrievalService = retrievalService;
        this.summaryService = summaryService;
    }

    /**
     * POST /api/repositories — Create a new repository for analysis.
     */
    @PostMapping
    public ResponseEntity<RepositoryResponse> createRepository(
            @Valid @RequestBody CreateRepositoryRequest request) {
        RepositoryResponse response = repositoryService.createRepository(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/repositories — List all repositories.
     */
    @GetMapping
    public ResponseEntity<List<RepositoryResponse>> getAllRepositories() {
        List<RepositoryResponse> repositories = repositoryService.getAllRepositories();
        return ResponseEntity.ok(repositories);
    }

    /**
     * GET /api/repositories/{id} — Get a specific repository by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RepositoryResponse> getRepositoryById(@PathVariable UUID id) {
        RepositoryResponse response = repositoryService.getRepositoryById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/repositories/{id}/search — Perform semantic search on repository code chunks.
     */
    @GetMapping("/{id}/search")
    public ResponseEntity<List<SearchResultDto>> searchRepository(
            @PathVariable UUID id,
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "5") Integer topK) {
        List<SearchResultDto> results = retrievalService.search(id, query, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/repositories/{id}/summary — Get or generate Architecture Summary / README for repository.
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<com.github.repository_ai.dto.ArchitectureSummaryDto> getArchitectureSummary(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") boolean forceRegenerate) {
        com.github.repository_ai.dto.ArchitectureSummaryDto summary = summaryService.getOrGenerateSummary(id, forceRegenerate);
        return ResponseEntity.ok(summary);
    }

    /**
     * DELETE /api/repositories/{id} — Delete a repository.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID id) {
        repositoryService.deleteRepository(id);
        return ResponseEntity.noContent().build();
    }
}
