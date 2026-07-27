package com.github.repository_ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Service for storing and searching vector embeddings in Neon PostgreSQL via Spring AI PgVectorStore.
 * Features concurrent multi-threaded batch processing for fast embedding generation and vector storage.
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private static final int BATCH_SIZE = 100;
    private static final int CONCURRENCY_LEVEL = 1;

    private final VectorStore vectorStore;

    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Stores a list of documents into PgVectorStore in parallel batches for max throughput.
     *
     * @param documents list of Spring AI Document objects to persist
     */
    public void saveDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("[VECTOR] No documents provided to store.");
            return;
        }

        log.info("[VECTOR] Storing {} vector documents into PgVectorStore in parallel (batchSize={}, concurrency={})...",
                documents.size(), BATCH_SIZE, CONCURRENCY_LEVEL);
        long startTime = System.currentTimeMillis();

        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, documents.size());
            batches.add(documents.subList(i, end));
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(CONCURRENCY_LEVEL, batches.size()));

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int b = 0; b < batches.size(); b++) {
                final int batchNum = b + 1;
                final List<Document> batch = batches.get(b);

                futures.add(executor.submit(() -> {
                    long batchStart = System.currentTimeMillis();
                    vectorStore.add(batch);
                    long batchElapsed = System.currentTimeMillis() - batchStart;
                    log.debug("[VECTOR] Batch {}/{} ({} docs) stored successfully in {}ms",
                            batchNum, batches.size(), batch.size(), batchElapsed);
                    return null;
                }));
            }

            for (Future<Void> future : futures) {
                future.get(); // Block until all batches complete or throw exception
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Vector storage interrupted", e);
        } catch (ExecutionException e) {
            log.error("[VECTOR] Vector batch storage failed: {}", e.getCause().getMessage(), e.getCause());
            throw new RuntimeException("Failed to persist vector batch: " + e.getCause().getMessage(), e.getCause());
        } finally {
            executor.shutdown();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[VECTOR] Successfully stored all {} vector documents in {}ms", documents.size(), elapsed);
    }
}
