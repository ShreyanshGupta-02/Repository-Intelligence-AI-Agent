package com.github.repository_ai.service;

import com.github.repository_ai.dto.ChatRequestDto;
import com.github.repository_ai.dto.ChatResponseDto;
import com.github.repository_ai.dto.SearchResultDto;
import com.github.repository_ai.entity.ChatMessage;
import com.github.repository_ai.entity.ChatSession;
import com.github.repository_ai.entity.MessageRole;
import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.entity.User;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.ChatMessageRepository;
import com.github.repository_ai.repository.ChatSessionRepository;
import com.github.repository_ai.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for RAG (Retrieval-Augmented Generation) chat answering using Groq via Spring AI ChatModel.
 * <p>
 * Pipeline:
 * 1. Performs repository-scoped semantic retrieval for relevant code chunks (Phase 8).
 * 2. Builds a context-grounded prompt (System Message + Retrieved Code Chunks + User Question).
 * 3. Calls Groq ChatModel with retry-with-backoff for 429 rate limit errors.
 * 4. Persists the conversation in ChatSession and ChatMessage entities.
 * 5. Returns ChatResponseDto with AI answer and source chunk attributions.
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1500;



    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final RepositoryRepository repositoryRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public RagChatService(@org.springframework.beans.factory.annotation.Qualifier("openAiChatModel") ChatModel chatModel,
                          RetrievalService retrievalService,
                          RepositoryRepository repositoryRepository,
                          ChatSessionRepository chatSessionRepository,
                          ChatMessageRepository chatMessageRepository) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.repositoryRepository = repositoryRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Processes a user chat request against a specific repository using RAG.
     *
     * @param repositoryId the ID of the target repository
     * @param request      the ChatRequestDto containing the question and optional sessionId / topK
     * @return ChatResponseDto containing the generated answer, session ID, and source attributions
     */
    @Transactional
    public ChatResponseDto chat(UUID repositoryId, ChatRequestDto request) {
        log.info("[RAG] Received chat request for repo id='{}', question='{}'", repositoryId, request.question());

        // 1. Verify repository exists
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId));

        // 2. Resolve or create ChatSession
        ChatSession session = resolveOrCreateSession(repository, request.sessionId(), request.question());

        // 3. Perform semantic retrieval (Phase 8)
        int topK = request.topK() != null ? request.topK() : 5;
        List<SearchResultDto> sources = retrievalService.search(repositoryId, request.question(), topK);
        log.info("[RAG] Retrieved {} source chunks for repo '{}'", sources.size(), repository.getName());

        // 4. Construct grounded prompt scoped explicitly to target repository
        Prompt prompt = buildGroundedPrompt(repository, request.question(), sources);

        // 5. Call Groq LLM with retry-with-backoff for 429 rate limit errors
        String answer = callChatModelWithRetry(prompt);

        // 6. Persist user message and assistant answer to ChatMessage database table
        saveChatMessage(session, MessageRole.USER, request.question());
        saveChatMessage(session, MessageRole.ASSISTANT, answer);

        log.info("[RAG] Chat response generated successfully for session id={}", session.getId());

        return new ChatResponseDto(
                session.getId(),
                request.question(),
                answer,
                sources
        );
    }

    // ==================== Prompt Engineering ====================

    /**
     * Constructs a context-grounded Prompt consisting of SystemMessage and UserMessage containing code context,
     * strictly scoped to the active repository name.
     */
    private Prompt buildGroundedPrompt(Repository repository, String question, List<SearchResultDto> sources) {
        String formattedSystemPrompt = String.format("""
                You are an expert AI software engineer analyzing the specific repository: '%s'.
                Your ONLY task is to answer the user's question accurately using ONLY the provided code context snippets below from repository '%s'.

                STRICT ANTI-HALLUCINATION & SCOPING GUIDELINES:
                1. Ground your answer strictly in the provided code context snippets from repository '%s'.
                2. If the user asks a question about a DIFFERENT repository, a different framework, or something NOT covered in the provided snippets for '%s', state clearly and honestly:
                   "Based on the provided codebase context for repository '%s', I do not have enough information to answer this question."
                3. Do NOT use outside pre-trained knowledge to answer questions about external or unreferenced repositories.
                4. Reference relevant file paths and line numbers in your response to explain code structure clearly.
                5. Use Markdown code blocks for any code examples.
                """, repository.getName(), repository.getName(), repository.getName(), repository.getName(), repository.getName());

        SystemMessage systemMessage = new SystemMessage(formattedSystemPrompt);

        StringBuilder userContent = new StringBuilder();
        userContent.append("Active Repository: ").append(repository.getName()).append("\n\n");
        userContent.append("User Question:\n").append(question).append("\n\n");

        if (sources.isEmpty()) {
            userContent.append("Codebase Context Snippets:\n[No relevant code snippets were found in repository '")
                    .append(repository.getName()).append("' for this query.]\n");
        } else {
            userContent.append("Codebase Context Snippets:\n");
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

    // ==================== LLM Call with Retry ====================

    /**
     * Calls Groq ChatModel with exponential backoff retry logic for 429 rate limit exceptions.
     */
    private String callChatModelWithRetry(Prompt prompt) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (true) {
            attempt++;
            try {
                log.debug("[RAG] Calling Groq ChatModel (attempt {}/{})", attempt, MAX_RETRIES);
                var response = chatModel.call(prompt);
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    return response.getResult().getOutput().getText();
                }
                throw new IllegalStateException("Received empty response from Groq ChatModel");

            } catch (Exception ex) {
                boolean isRateLimit = ex.getMessage() != null &&
                        (ex.getMessage().contains("429") || ex.getMessage().toLowerCase().contains("rate limit"));

                if (isRateLimit && attempt < MAX_RETRIES) {
                    log.warn("[RAG] Rate limit (429) encountered on attempt {}/{}. Retrying in {}ms...",
                            attempt, MAX_RETRIES, backoff);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("RAG retry interrupted", ie);
                    }
                    backoff *= 2; // Exponential backoff
                } else {
                    log.error("[RAG] Groq ChatModel call failed on attempt {}/{}: {}", attempt, MAX_RETRIES, ex.getMessage(), ex);
                    throw new RuntimeException("Failed to generate AI response from Groq: " + ex.getMessage(), ex);
                }
            }
        }
    }

    // ==================== Session Helpers ====================

    private ChatSession resolveOrCreateSession(Repository repository, UUID sessionId, String question) {
        if (sessionId != null) {
            return chatSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));
        }

        User currentUser = getCurrentUser();

        // Auto-create a title from the first 50 chars of the question
        String title = question.length() > 50 ? question.substring(0, 47) + "..." : question;

        ChatSession newSession = ChatSession.builder()
                .title(title)
                .repository(repository)
                .user(currentUser)
                .build();

        ChatSession saved = chatSessionRepository.save(newSession);
        log.info("[RAG] Created new ChatSession id={} for repo '{}' and user={}", saved.getId(), repository.getName(), currentUser != null ? currentUser.getUsername() : "anonymous");
        return saved;
    }

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private void saveChatMessage(ChatSession session, MessageRole role, String content) {
        ChatMessage message = ChatMessage.builder()
                .chatSession(session)
                .role(role)
                .content(content)
                .build();
        chatMessageRepository.save(message);
    }
}
