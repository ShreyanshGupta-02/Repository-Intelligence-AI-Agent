package com.github.repository_ai.service;

import com.github.repository_ai.dto.SearchResultDto;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for performing semantic search / retrieval over indexed repository code chunks.
 * <p>
 * GUARANTEES:
 * - Scoped strictly to the target repository_id (prevents cross-repo chunk leakage)
 * - Configurable topK (number of results returned)
 * - Uses cosine similarity distance via Spring AI PgVectorStore
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;
    private final RepositoryRepository repositoryRepository;

    public RetrievalService(VectorStore vectorStore, RepositoryRepository repositoryRepository) {
        this.vectorStore = vectorStore;
        this.repositoryRepository = repositoryRepository;
    }

    /**
     * Performs semantic search over a repository's indexed code chunks.
     *
     * @param repositoryId the ID of the target repository
     * @param query        the search query string
     * @param topK         maximum number of matching chunks to return (defaults to 5 if null or <= 0)
     * @return list of matching SearchResultDto items ordered by relevance score
     */
    public List<SearchResultDto> search(UUID repositoryId, String query, Integer topK) {
        log.info("[RETRIEVAL] Searching repo id={} with query='{}', topK={}", repositoryId, query, topK);

        // 1. Verify repository exists
        if (!repositoryRepository.existsById(repositoryId)) {
            log.warn("[RETRIEVAL] Repository not found: id={}", repositoryId);
            throw new ResourceNotFoundException("Repository", repositoryId);
        }

        int k = (topK != null && topK > 0) ? topK : DEFAULT_TOP_K;

        long startTime = System.currentTimeMillis();

        // 2. Build repository-scoped SearchRequest with FilterExpressionBuilder
        org.springframework.ai.vectorstore.filter.FilterExpressionBuilder builder =
                new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(k)
                .filterExpression(builder.eq("repositoryId", repositoryId.toString()).build())
                .build();

        // 3. Execute vector similarity search
        List<Document> rawDocuments = vectorStore.similaritySearch(searchRequest);
        long elapsed = System.currentTimeMillis() - startTime;

        // 4. Double-guard repository scoping at code level
        List<Document> filteredDocuments = rawDocuments.stream()
                .filter(doc -> {
                    Object repoIdMeta = doc.getMetadata().get("repositoryId");
                    return repoIdMeta != null && repositoryId.toString().equalsIgnoreCase(repoIdMeta.toString());
                })
                .toList();

        log.info("[RETRIEVAL] Search returned {} chunks ({} after repository filter) for repo id={} in {}ms",
                rawDocuments.size(), filteredDocuments.size(), repositoryId, elapsed);

        // 5. Map Spring AI Documents to DTOs
        return filteredDocuments.stream()
                .map(this::mapToSearchResultDto)
                .toList();
    }

    private SearchResultDto mapToSearchResultDto(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        String chunkId = metadata.getOrDefault("chunkId", doc.getId()).toString();
        String filePath = metadata.getOrDefault("filePath", "").toString();
        String fileName = metadata.getOrDefault("fileName", "").toString();
        String language = metadata.getOrDefault("language", "").toString();

        Integer startLine = parseInteger(metadata.get("startLine"));
        Integer endLine = parseInteger(metadata.get("endLine"));

        Double score = doc.getScore() != null ? doc.getScore() : null;

        return new SearchResultDto(
                chunkId,
                filePath,
                fileName,
                language,
                startLine,
                endLine,
                doc.getText(),
                score
        );
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Integer i) return i;
        if (obj instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
