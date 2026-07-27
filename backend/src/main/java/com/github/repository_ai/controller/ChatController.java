package com.github.repository_ai.controller;

import com.github.repository_ai.dto.ChatMessageDto;
import com.github.repository_ai.dto.ChatRequestDto;
import com.github.repository_ai.dto.ChatResponseDto;
import com.github.repository_ai.dto.ChatSessionResponseDto;
import com.github.repository_ai.entity.ChatSession;
import com.github.repository_ai.entity.User;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.ChatMessageRepository;
import com.github.repository_ai.repository.ChatSessionRepository;
import com.github.repository_ai.service.RagChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for RAG chat interactions and conversation session history.
 * Controllers ONLY receive/return DTOs and delegate business logic to services.
 */
@RestController
@RequestMapping("/repositories/{repositoryId}/chat")
public class ChatController {

    private final RagChatService ragChatService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatController(RagChatService ragChatService,
                          ChatSessionRepository chatSessionRepository,
                          ChatMessageRepository chatMessageRepository) {
        this.ragChatService = ragChatService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * POST /api/repositories/{repositoryId}/chat — Perform context-grounded RAG Q&A via Groq.
     */
    @PostMapping
    public ResponseEntity<ChatResponseDto> chat(
            @PathVariable UUID repositoryId,
            @Valid @RequestBody ChatRequestDto request) {
        ChatResponseDto response = ragChatService.chat(repositoryId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/repositories/{repositoryId}/chat/sessions — List all chat sessions for a repository and authenticated user.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponseDto>> getChatSessions(@PathVariable UUID repositoryId) {
        User currentUser = getCurrentUser();
        var sessionsList = (currentUser != null)
                ? chatSessionRepository.findAllByRepositoryIdAndUserIdOrderByCreatedAtDesc(repositoryId, currentUser.getId())
                : chatSessionRepository.findAllByRepositoryIdOrderByCreatedAtDesc(repositoryId);

        List<ChatSessionResponseDto> sessions = sessionsList
                .stream()
                .map(s -> new ChatSessionResponseDto(s.getId(), s.getTitle(), s.getRepository().getId(), s.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    /**
     * GET /api/repositories/{repositoryId}/chat/sessions/{sessionId}/messages — Get all messages for a specific chat session.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getSessionMessages(
            @PathVariable UUID repositoryId,
            @PathVariable UUID sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));

        if (!session.getRepository().getId().equals(repositoryId)) {
            throw new IllegalArgumentException("Session does not belong to the specified repository");
        }

        List<ChatMessageDto> messages = chatMessageRepository
                .findAllByChatSessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(m -> new ChatMessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(messages);
    }

    /**
     * DELETE /api/repositories/{repositoryId}/chat/sessions/{sessionId} — Delete a specific chat session.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteChatSession(
            @PathVariable UUID repositoryId,
            @PathVariable UUID sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", sessionId));

        if (!session.getRepository().getId().equals(repositoryId)) {
            throw new IllegalArgumentException("Session does not belong to the specified repository");
        }

        chatSessionRepository.delete(session);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}
