package com.github.repository_ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for storing and searching vector embeddings in Neon PostgreSQL via Spring AI PgVectorStore.
 * <p>
 * Performance strategy (optimized for local Ollama CPU-based embeddings):
 * <ul>
 *   <li>Uses large batches (50 docs) to minimize HTTP round-trips to Ollama</li>
 *   <li>Sequential processing — Ollama serializes embedding requests on CPU,
 *       so concurrency only creates queue contention and wastes time</li>
 *   <li>Each vectorStore.add() call: Ollama embeds the batch → inserts into PgVector</li>
 * </ul>
 * <p>
 * Why NOT concurrent: Ollama on CPU processes embeddings one request at a time.
 * With 4 concurrent threads, batches queue up in Ollama and individual batch times
 * balloon from ~10s to ~80s+ due to queue wait. Sequential with large batches is faster.
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    /**
     * Number of documents per batch for vectorStore.add().
     * Each batch = 1 Ollama API call (embedding) + 1 PgVector DB insert.
     * <p>
     * 50 is optimal: large enough to amortize HTTP/Ollama overhead per doc,
     * small enough for reliable DB transactions to remote Neon. Ollama's /api/embed
     * endpoint handles batch input natively.
     */
    private static final int BATCH_SIZE = 50;

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Stores documents into PgVectorStore in sequential batches.
     * <p>
     * Each batch call to vectorStore.add() internally:
     * 1. Sends texts to Ollama's /api/embed endpoint for embedding
     * 2. Inserts the embedded vectors into PgVector (Neon DB)
     *
     * @param documents list of Spring AI Document objects to persist
     */
    public void saveDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("[VECTOR] No documents provided to store.");
            return;
        }

        int totalDocs = documents.size();
        log.info("[VECTOR] Storing {} documents into PgVectorStore (batchSize={})...",
                totalDocs, BATCH_SIZE);
        long startTime = System.currentTimeMillis();

        // Split into batches
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < totalDocs; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalDocs);
            batches.add(new ArrayList<>(documents.subList(i, end)));
        }

        int totalBatches = batches.size();
        log.info("[VECTOR] Split into {} batches of up to {} docs", totalBatches, BATCH_SIZE);

        int storedCount = 0;

        for (int b = 0; b < totalBatches; b++) {
            int batchNum = b + 1;
            List<Document> batch = batches.get(b);
            long batchStart = System.currentTimeMillis();

            try {
                vectorStore.add(batch);

                storedCount += batch.size();
                long batchElapsed = System.currentTimeMillis() - batchStart;
                long totalElapsed = System.currentTimeMillis() - startTime;

                log.info("[VECTOR] Batch {}/{} ({} docs) done in {}ms | Progress: {}/{} docs | Elapsed: {}s",
                        batchNum, totalBatches, batch.size(), batchElapsed,
                        storedCount, totalDocs,
                        String.format("%.1f", totalElapsed / 1000.0));

            } catch (Exception e) {
                long batchElapsed = System.currentTimeMillis() - batchStart;
                log.error("[VECTOR] Batch {}/{} FAILED after {}ms: {}",
                        batchNum, totalBatches, batchElapsed, e.getMessage(), e);
                // Continue with remaining batches
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double docsPerSec = storedCount / Math.max(elapsed / 1000.0, 0.001);

        if (storedCount == 0) {
            throw new RuntimeException(
                    "All embedding batches failed. Check Ollama connectivity at localhost:11434");
        }

        if (storedCount < totalDocs) {
            log.warn("[VECTOR] Partially completed: {}/{} docs stored in {}ms",
                    storedCount, totalDocs, elapsed);
        } else {
            log.info("[VECTOR] Completed: {} docs stored in {}ms ({} docs/sec)",
                    storedCount, elapsed, String.format("%.1f", docsPerSec));
        }
    }
}
