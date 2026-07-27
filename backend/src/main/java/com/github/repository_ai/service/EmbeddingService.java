package com.github.repository_ai.service;

import com.github.repository_ai.entity.CodeChunk;
import com.github.repository_ai.entity.Repository;
import com.github.repository_ai.repository.CodeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates vector embeddings for code chunks using Spring AI's EmbeddingModel
 * (backed by Ollama's nomic-embed-text locally).
 * <p>
 * Key features:
 * <ul>
 *   <li>Batch processing to avoid overwhelming Ollama</li>
 *   <li>768-dimensional vectors (nomic-embed-text)</li>
 *   <li>Prepares Spring AI {@link Document} objects with metadata for PgVectorStore (Phase 7)</li>
 *   <li>Comprehensive logging and error handling</li>
 * </ul>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    /** Number of chunks to embed in a single batch call to Ollama. */
    private static final int BATCH_SIZE = 20;

    private final EmbeddingModel embeddingModel;
    private final CodeChunkRepository codeChunkRepository;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            CodeChunkRepository codeChunkRepository) {
        this.embeddingModel = embeddingModel;
        this.codeChunkRepository = codeChunkRepository;
    }

    /**
     * Generates embeddings for all code chunks of a repository.
     * Processes chunks in batches and returns the list of Spring AI Documents
     * ready for storage in PgVectorStore (Phase 7).
     *
     * @param repository the repository whose chunks should be embedded
     * @return list of Documents with embeddings and metadata
     */
    /**
     * Converts code chunks of a repository into Spring AI Document objects
     * with rich metadata (repositoryId, filePath, language, line numbers),
     * ready for batch storage and vector generation in PgVectorStore.
     *
     * @param repository the repository whose chunks should be prepared
     * @return list of Spring AI Documents
     */
    public List<Document> embedRepository(Repository repository) {
        log.info("[EMBED] Preparing documents for repository '{}'", repository.getName());
        long startTime = System.currentTimeMillis();

        List<CodeChunk> chunks = codeChunkRepository
                .findAllWithFileByRepositoryId(repository.getId());

        if (chunks.isEmpty()) {
            log.warn("[EMBED] No chunks found for '{}', skipping document preparation", repository.getName());
            return List.of();
        }

        log.info("[EMBED] Found {} chunks to prepare for '{}'", chunks.size(), repository.getName());

        List<Document> documents = new ArrayList<>(chunks.size());

        for (CodeChunk chunk : chunks) {
            Document doc = Document.builder()
                    .id(chunk.getId().toString())
                    .text(chunk.getContent())
                    .metadata(Map.of(
                            "chunkId", chunk.getId().toString(),
                            "repositoryId", repository.getId().toString(),
                            "filePath", chunk.getRepositoryFile().getFilePath(),
                            "fileName", chunk.getRepositoryFile().getFileName(),
                            "language", chunk.getRepositoryFile().getLanguage() != null
                                    ? chunk.getRepositoryFile().getLanguage() : "unknown",
                            "startLine", chunk.getStartLine() != null ? chunk.getStartLine() : 0,
                            "endLine", chunk.getEndLine() != null ? chunk.getEndLine() : 0
                    ))
                    .build();
            documents.add(doc);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[EMBED] Prepared {} documents for '{}' in {}ms",
                documents.size(), repository.getName(), elapsed);

        return documents;
    }

    /**
     * Queries the repository for code chunks that need embedding.
     * Useful for re-embedding specific chunks.
     */
    public long countChunksForRepository(UUID repositoryId) {
        return codeChunkRepository.countByRepositoryFileRepositoryId(repositoryId);
    }
}
