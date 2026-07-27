package com.github.repository_ai.service;

import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.entity.RepositoryStatus;
import com.github.repository_ai.repository.RepositoryRepository;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/**
 * Service for cloning GitHub repositories using JGit and orchestrating the
 * indexing pipeline.
 * <p>
 * Runs asynchronously — the controller returns immediately with PENDING status,
 * and the pipeline runs in a background thread. Status transitions:
 * PENDING → CLONING → PARSING → INDEXED/FAILED
 * <p>
 * Safety features:
 * - Pre-flight size check via GitHub API (rejects repos > configurable limit)
 * - Shallow clone (depth=1) to minimize disk/network usage
 * - Temp directory is ALWAYS cleaned up (finally block)
 */
@Service
public class GitCloneService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

    private final RepositoryRepository repositoryRepository;
    private final FileParserService fileParserService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Value("${app.clone.max-repo-size-kb:512000}")
    private long maxRepoSizeKb;

    @Value("${app.clone.temp-dir-prefix:repo-clone-}")
    private String tempDirPrefix;

    public GitCloneService(RepositoryRepository repositoryRepository,
                           FileParserService fileParserService,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService,
                           VectorStoreService vectorStoreService) {
        this.repositoryRepository = repositoryRepository;
        this.fileParserService = fileParserService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Asynchronously clones a GitHub repository, parses files, chunks code,
     * and generates embeddings.
     * This method returns immediately — the actual work runs on the git-clone thread pool.
     *
     * Pipeline: PENDING → CLONING → PARSING → EMBEDDING → INDEXED/FAILED
     *
     * @param repositoryId the ID of the Repository entity to process
     */
    @Async("gitCloneExecutor")
    public void cloneRepositoryAsync(UUID repositoryId) {
        log.info("[PIPELINE] Starting async clone+parse for repository id={}", repositoryId);

        Path tempDir = null;
        try {
            // 1. Load the entity
            Repository repo = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> {
                        log.error("[PIPELINE] Repository not found: id={}", repositoryId);
                        return new IllegalStateException("Repository not found: " + repositoryId);
                    });

            // 2. Transition: PENDING → CLONING
            updateStatus(repo, RepositoryStatus.CLONING, null);
            log.info("[PIPELINE] Status → CLONING for '{}' (id={})", repo.getName(), repo.getId());

            // 3. Pre-flight: validate repo size via GitHub API
            validateRepositorySize(repo.getGithubUrl(), repo.getName());

            // 4. Create temp directory
            tempDir = Files.createTempDirectory(tempDirPrefix);
            log.debug("[PIPELINE] Created temp directory: {}", tempDir);

            // 5. Perform shallow clone (depth=1, single branch)
            log.info("[PIPELINE] Cloning '{}' into {} ...", repo.getGithubUrl(), tempDir);
            long cloneStart = System.currentTimeMillis();

            String cloneUrl = repo.getGithubUrl().trim();
            if (!cloneUrl.endsWith(".git")) {
                cloneUrl = cloneUrl + ".git";
            }

            try (Git git = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(tempDir.toFile())
                    .setDepth(1)
                    .setCloneAllBranches(false)
                    .call()) {

                long cloneElapsed = System.currentTimeMillis() - cloneStart;
                log.info("[PIPELINE] Clone completed for '{}' in {}ms", repo.getName(), cloneElapsed);
            }

            // 6. Transition: CLONING → PARSING
            updateStatus(repo, RepositoryStatus.PARSING, null);
            log.info("[PIPELINE] Status → PARSING for '{}' (id={})", repo.getName(), repo.getId());

            // 7. Parse source files from cloned directory
            int fileCount = fileParserService.parseRepository(repo, tempDir);
            log.info("[PIPELINE] Parsed {} files for '{}'", fileCount, repo.getName());

            // 8. Chunk parsed files into code chunks
            int chunkCount = chunkingService.chunkRepository(repo);
            log.info("[PIPELINE] Created {} chunks for '{}'", chunkCount, repo.getName());

            // 9. Transition: PARSING → EMBEDDING
            updateStatus(repo, RepositoryStatus.EMBEDDING, null);
            log.info("[PIPELINE] Status → EMBEDDING for '{}' (id={})", repo.getName(), repo.getId());

            // 10. Generate embeddings for all chunks via Ollama
            var documents = embeddingService.embedRepository(repo);
            log.info("[PIPELINE] Generated {} embeddings for '{}'", documents.size(), repo.getName());

            // 11. Persist vector embeddings into Neon PgVectorStore
            if (!documents.isEmpty()) {
                vectorStoreService.saveDocuments(documents);
                log.info("[PIPELINE] Persisted {} vector embeddings into PgVectorStore for '{}'", documents.size(), repo.getName());
            }

            // 12. Transition: EMBEDDING → INDEXED
            updateStatus(repo, RepositoryStatus.INDEXED, null);
            log.info("[PIPELINE] Status → INDEXED for '{}' (id={})", repo.getName(), repo.getId());

        } catch (Exception ex) {
            log.error("[PIPELINE] FAILED for repository id={}: {}", repositoryId, ex.getMessage(), ex);
            handleCloneFailure(repositoryId, ex.getMessage());
        } finally {
            // 9. ALWAYS clean up temp directory
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }
    }

    // ==================== Pre-flight Validation ====================

    /**
     * Checks the repository size via the GitHub REST API (unauthenticated).
     * Rejects repos that exceed the configured max size.
     *
     * @throws IllegalStateException if the repo is too large or the API call fails
     */
    private void validateRepositorySize(String githubUrl, String repoName) {
        log.debug("[CLONE] Validating repository size for '{}'", repoName);

        try {
            // Extract owner/repo from URL: https://github.com/owner/repo → owner/repo
            String ownerRepo = githubUrl.replace("https://github.com/", "").trim();
            if (ownerRepo.endsWith("/")) {
                ownerRepo = ownerRepo.substring(0, ownerRepo.length() - 1);
            }
            if (ownerRepo.endsWith(".git")) {
                ownerRepo = ownerRepo.substring(0, ownerRepo.length() - 4);
            }

            String apiUrl = "https://api.github.com/repos/" + ownerRepo;
            log.debug("[CLONE] Calling GitHub API: {}", apiUrl);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "RepositoryIntelligenceAI")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[CLONE] GitHub API returned status {} for '{}'. Skipping size check.",
                        response.statusCode(), repoName);
                return; // Don't block clone if GitHub API is unavailable
            }

            // Parse "size" field from JSON (size is in KB)
            long sizeKb = extractSizeFromJson(response.body());
            double sizeMb = sizeKb / 1024.0;

            log.info("[CLONE] Repository '{}' size: {} MB ({} KB)", repoName, String.format("%.1f", sizeMb), sizeKb);

            if (sizeKb > maxRepoSizeKb) {
                throw new IllegalStateException(String.format(
                        "Repository '%s' is too large (%.1f MB). Maximum allowed: %.1f MB",
                        repoName, sizeMb, maxRepoSizeKb / 1024.0));
            }

            log.debug("[CLONE] Size check passed for '{}'", repoName);

        } catch (IllegalStateException ex) {
            throw ex; // Re-throw size limit exception
        } catch (Exception ex) {
            log.warn("[CLONE] Could not validate repo size for '{}': {}. Proceeding with clone.",
                    repoName, ex.getMessage());
            // Don't block cloning if size check fails (network issue, rate limit, etc.)
        }
    }

    /**
     * Extracts the "size" field from GitHub API JSON response.
     * Simple parsing without adding a JSON library dependency.
     */
    private long extractSizeFromJson(String json) {
        // Look for "size": <number> pattern
        String sizePattern = "\"size\":";
        int idx = json.indexOf(sizePattern);
        if (idx == -1) {
            log.warn("[CLONE] Could not find 'size' field in GitHub API response");
            return 0;
        }

        int start = idx + sizePattern.length();
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        StringBuilder number = new StringBuilder();
        while (start < json.length() && Character.isDigit(json.charAt(start))) {
            number.append(json.charAt(start));
            start++;
        }

        return number.isEmpty() ? 0 : Long.parseLong(number.toString());
    }

    // ==================== Status Management ====================

    /**
     * Updates the status of a repository entity.
     * Note: Not using @Transactional here because Spring proxies don't intercept
     * self-calls within the same class. Using saveAndFlush for immediate persistence.
     */
    private void updateStatus(Repository repo, RepositoryStatus status, String errorMessage) {
        repo.setStatus(status);
        repo.setErrorMessage(errorMessage);
        repositoryRepository.saveAndFlush(repo);
        log.debug("[CLONE] Updated status to {} for id={}", status, repo.getId());
    }

    /**
     * Handles clone failure by updating the repository status to FAILED.
     */
    private void handleCloneFailure(UUID repositoryId, String errorMessage) {
        try {
            repositoryRepository.findById(repositoryId).ifPresent(repo -> {
                updateStatus(repo, RepositoryStatus.FAILED, errorMessage);
                log.info("[CLONE] Status → FAILED for '{}' (id={})", repo.getName(), repo.getId());
            });
        } catch (Exception ex) {
            log.error("[CLONE] Could not update status to FAILED for id={}: {}", repositoryId, ex.getMessage());
        }
    }

    // ==================== Cleanup ====================

    /**
     * Recursively deletes a temp directory and all its contents.
     * Uses a file visitor to handle read-only files (common in .git directories).
     */
    private void cleanupTempDirectory(Path tempDir) {
        try {
            log.debug("[CLONE] Cleaning up temp directory: {}", tempDir);
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file.toFile().setWritable(true); // Handle read-only .git files
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("[CLONE] Temp directory cleaned up successfully: {}", tempDir);
        } catch (IOException ex) {
            log.error("[CLONE] Failed to clean up temp directory {}: {}", tempDir, ex.getMessage());
        }
    }
}
