package com.github.repository_ai.repository;

import com.github.repository_ai.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CodeChunk} entities.
 */
public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    List<CodeChunk> findAllByRepositoryFileId(UUID repositoryFileId);

    long countByRepositoryFileRepositoryId(UUID repositoryId);

    void deleteAllByRepositoryFileRepositoryId(UUID repositoryId);

    /**
     * Fetches all chunks for a repository with their RepositoryFile eagerly loaded.
     * Used by EmbeddingService to access file metadata without lazy-loading issues.
     */
    @Query("SELECT cc FROM CodeChunk cc JOIN FETCH cc.repositoryFile rf WHERE rf.repository.id = :repositoryId")
    List<CodeChunk> findAllWithFileByRepositoryId(@Param("repositoryId") UUID repositoryId);
}
