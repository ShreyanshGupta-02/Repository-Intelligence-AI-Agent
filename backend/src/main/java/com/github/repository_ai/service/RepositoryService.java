package com.github.repository_ai.service;

import com.github.repository_ai.dto.CreateRepositoryRequest;
import com.github.repository_ai.dto.RepositoryResponse;
import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.entity.RepositoryStatus;
import com.github.repository_ai.entity.User;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for repository CRUD operations.
 * All business logic lives here — controllers only delegate.
 */
@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final RepositoryRepository repositoryRepository;
    private final GitCloneService gitCloneService;

    // Constructor injection only — no @Autowired on fields
    public RepositoryService(RepositoryRepository repositoryRepository,
                             GitCloneService gitCloneService) {
        this.repositoryRepository = repositoryRepository;
        this.gitCloneService = gitCloneService;
    }

    /**
     * Creates a new repository record with PENDING status and triggers async cloning.
     * Extracts the repo name from the GitHub URL.
     *
     * @throws IllegalArgumentException if the URL is already registered
     */
    @Transactional
    public RepositoryResponse createRepository(CreateRepositoryRequest request) {
        log.debug("createRepository() called with githubUrl='{}'", request.githubUrl());

        String url = normalizeGithubUrl(request.githubUrl());
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("User must be authenticated to index a repository");
        }

        if (repositoryRepository.existsByGithubUrlAndUserId(url, currentUser.getId())) {
            log.warn("Duplicate repository URL rejected for user {}: {}", currentUser.getId(), url);
            throw new IllegalArgumentException("Repository already exists in your account: " + url);
        }

        String name = extractRepoName(url);

        Repository entity = Repository.builder()
                .githubUrl(url)
                .name(name)
                .status(RepositoryStatus.PENDING)
                .user(currentUser)
                .build();

        Repository saved = repositoryRepository.save(entity);
        log.info("Created repository: '{}' (id={}, user={}, status=PENDING)", saved.getName(), saved.getId(), currentUser.getUsername());

        // Trigger async cloning AFTER the transaction commits
        // (the async thread needs the row to exist in the DB)
        UUID savedId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                gitCloneService.cloneRepositoryAsync(savedId);
                log.debug("Async clone triggered for repository id={}", savedId);
            }
        });

        return toResponse(saved);
    }

    /**
     * Returns all repositories for the authenticated user ordered by creation date (newest first).
     */
    @Transactional(readOnly = true)
    public List<RepositoryResponse> getAllRepositories() {
        log.debug("getAllRepositories() called");
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            log.warn("getAllRepositories() called without authenticated user");
            return List.of();
        }

        List<RepositoryResponse> repos = repositoryRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
        log.debug("Returning {} repositories for user {}", repos.size(), currentUser.getUsername());
        return repos;
    }

    /**
     * Returns a single repository by ID if it belongs to the authenticated user.
     *
     * @throws ResourceNotFoundException if the repository doesn't exist
     */
    @Transactional(readOnly = true)
    public RepositoryResponse getRepositoryById(UUID id) {
        log.debug("getRepositoryById() called with id={}", id);
        User currentUser = getCurrentUser();
        Repository entity;
        if (currentUser != null) {
            entity = repositoryRepository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> {
                        log.warn("Repository not found for id={} and user={}", id, currentUser.getId());
                        return new ResourceNotFoundException("Repository", id);
                    });
        } else {
            entity = repositoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Repository", id));
        }
        return toResponse(entity);
    }

    /**
     * Deletes a repository by ID if it belongs to the authenticated user.
     *
     * @throws ResourceNotFoundException if the repository doesn't exist
     */
    @Transactional
    public void deleteRepository(UUID id) {
        log.debug("deleteRepository() called with id={}", id);
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            Repository entity = repositoryRepository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> {
                        log.warn("Attempted to delete non-existent or unauthorized repository id={} for user={}", id, currentUser.getId());
                        return new ResourceNotFoundException("Repository", id);
                    });
            repositoryRepository.delete(entity);
            log.info("Deleted repository id={} for user={}", id, currentUser.getUsername());
        } else {
            if (!repositoryRepository.existsById(id)) {
                log.warn("Attempted to delete non-existent repository: id={}", id);
                throw new ResourceNotFoundException("Repository", id);
            }
            repositoryRepository.deleteById(id);
            log.info("Deleted repository with id={}", id);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * Normalizes a GitHub URL by trimming whitespace, trailing slashes, and '.git' extension.
     */
    private String normalizeGithubUrl(String url) {
        if (url == null) return null;
        String clean = url.trim();
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    /**
     * Extracts "owner/repo" from a GitHub URL.
     * e.g. "https://github.com/spring-projects/spring-boot" → "spring-projects/spring-boot"
     */
    private String extractRepoName(String url) {
        String clean = normalizeGithubUrl(url);
        String path = clean.replace("https://github.com/", "");
        // Remove any trailing parts beyond owner/repo
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            String repoName = parts[1];
            if (repoName.endsWith(".git")) {
                repoName = repoName.substring(0, repoName.length() - 4);
            }
            return parts[0] + "/" + repoName;
        }
        return path;
    }

    /**
     * Maps a Repository entity to its response DTO.
     */
    private RepositoryResponse toResponse(Repository entity) {
        return new RepositoryResponse(
                entity.getId(),
                entity.getGithubUrl(),
                entity.getName(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getArchitectureSummary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
