package com.github.repository_ai.service;

import com.github.repository_ai.dto.ArchitectureSummaryDto;
import com.github.repository_ai.dto.SearchResultDto;
import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for generating and caching comprehensive Architecture Overviews & README Summaries.
 * <p>
 * Pipeline:
 * 1. Checks if architectureSummary is already cached on the Repository entity.
 * 2. If cached and forceRegenerate=false, returns cached summary immediately.
 * 3. Performs multi-keyword semantic retrieval (frameworks, entry points, APIs, architecture).
 * 4. Calls Groq ChatModel with a structured Architecture Summary prompt.
 * 5. Persists generated summary to database row for instant future retrieval (Caching).
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String ARCHITECTURE_RETRIEVAL_QUERY =
            "frameworks libraries entry points main function database API routes architecture configuration dependencies build tool";

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final RepositoryRepository repositoryRepository;

    public SummaryService(@Qualifier("openAiChatModel") ChatModel chatModel,
                          RetrievalService retrievalService,
                          RepositoryRepository repositoryRepository) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.repositoryRepository = repositoryRepository;
    }

    /**
     * Retrieves or generates an Architecture Overview for a repository.
     * Caches the generated Markdown summary directly in the database row.
     *
     * @param repositoryId    the target repository ID
     * @param forceRegenerate if true, ignores cached summary and regenerates via Groq
     * @return ArchitectureSummaryDto containing the Markdown summary and cache status
     */
    @Transactional
    public ArchitectureSummaryDto getOrGenerateSummary(UUID repositoryId, boolean forceRegenerate) {
        log.info("[SUMMARY] Fetching architecture summary for repo id={}, forceRegenerate={}", repositoryId, forceRegenerate);

        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        // Return cached summary if available
        if (!forceRegenerate && repository.getArchitectureSummary() != null && !repository.getArchitectureSummary().isBlank()) {
            log.info("[SUMMARY] Returning CACHED architecture summary for '{}'", repository.getName());
            return new ArchitectureSummaryDto(
                    repository.getId(),
                    repository.getName(),
                    repository.getArchitectureSummary(),
                    true
            );
        }

        log.info("[SUMMARY] Generating NEW architecture summary for '{}' via Groq LLM...", repository.getName());

        // 1. Retrieve architectural code context
        List<SearchResultDto> sources = retrievalService.search(repositoryId, ARCHITECTURE_RETRIEVAL_QUERY, 10);

        // 2. Build structured architecture prompt
        Prompt prompt = buildArchitecturePrompt(repository, sources);

        // 3. Call Groq ChatModel
        long startTime = System.currentTimeMillis();
        var response = chatModel.call(prompt);
        long elapsed = System.currentTimeMillis() - startTime;

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Failed to receive architecture summary from Groq ChatModel");
        }

        String summary = response.getResult().getOutput().getText();
        log.info("[SUMMARY] Successfully generated summary for '{}' in {}ms", repository.getName(), elapsed);

        // 4. Cache generated summary in database
        repository.setArchitectureSummary(summary);
        repositoryRepository.save(repository);

        return new ArchitectureSummaryDto(
                repository.getId(),
                repository.getName(),
                summary,
                false
        );
    }

    // ==================== Prompt Construction ====================

    private Prompt buildArchitecturePrompt(Repository repository, List<SearchResultDto> sources) {
        String systemContent = String.format("""
                You are a Senior Principal Software Architect.
                Analyze the provided code snippets for repository '%s' and generate a professional, highly structured Architecture Overview & Tech Stack README Summary.

                MANDATORY MARKDOWN SECTIONS:
                # 🚀 %s — Architecture Overview & README

                ## 1. 📋 Executive Summary
                High-level description of what this project does and its core capabilities.

                ## 2. 🛠️ Tech Stack & Key Technologies
                List languages, frameworks, databases, and major dependencies identified in the codebase.

                ## 3. 🏗️ Architecture & Component Design
                Explain how components, services, and packages are organized.

                ## 4. 🔌 Key API Endpoints & Core Handlers
                Summarize the primary REST routes, controllers, or entry point functions.

                ## 5. ⚙️ Configuration & Execution Flow
                Explain how the application starts, loads configurations, or handles environment setups.

                STRICT GUIDELINES:
                - Ground your analysis strictly in the provided codebase context snippets for '%s'.
                - Format cleanly using GitHub-Flavored Markdown with headers, bullet points, and code blocks.
                """, repository.getName(), repository.getName(), repository.getName());

        SystemMessage systemMessage = new SystemMessage(systemContent);

        StringBuilder userContent = new StringBuilder();
        userContent.append("Target Repository: ").append(repository.getName()).append("\n\n");
        userContent.append("Codebase Structural Context Snippets:\n");

        if (sources.isEmpty()) {
            userContent.append("[No structural code snippets found in index]\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                SearchResultDto src = sources.get(i);
                userContent.append(String.format("--- [Snippet %d: %s (Lines %d-%d)] ---\n",
                        i + 1, src.filePath(), src.startLine(), src.endLine()));
                userContent.append(src.content()).append("\n\n");
            }
        }

        UserMessage userMessage = new UserMessage(userContent.toString());
        return new Prompt(List.of(systemMessage, userMessage));
    }
}
