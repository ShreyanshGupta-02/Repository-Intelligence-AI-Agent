package com.github.repository_ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stub entity representing a chunk of source code extracted from a RepositoryFile.
 * Fully implemented in Phase 5 (Chunk Source Code).
 * <p>
 * NOTE: The embedding vector column (VECTOR(768)) is NOT mapped as a JPA field.
 * It is managed via native SQL / Spring AI VectorStore in Phase 7.
 */
@Entity
@Table(name = "code_chunks", indexes = {
        @Index(name = "idx_code_chunk_repo_file_id", columnList = "repository_file_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_file_id", nullable = false)
    private RepositoryFile repositoryFile;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
