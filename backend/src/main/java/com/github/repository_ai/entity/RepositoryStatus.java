package com.github.repository_ai.entity;

/**
 * Status transitions for a repository during the indexing pipeline:
 * PENDING → CLONING → PARSING → EMBEDDING → INDEXED
 *                 ↘       ↘         ↘
 *                  FAILED   FAILED    FAILED
 */
public enum RepositoryStatus {
    PENDING,
    CLONING,
    PARSING,
    EMBEDDING,
    INDEXED,
    FAILED
}
