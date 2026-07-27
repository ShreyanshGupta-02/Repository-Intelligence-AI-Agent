package com.github.repository_ai.repository;

import com.github.repository_ai.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Repository} entities.
 */
public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    /**
     * Returns all repositories ordered by creation date (newest first).
     */
    List<Repository> findAllByOrderByCreatedAtDesc();

    /**
     * Returns repositories belonging to a specific user ordered by creation date (newest first).
     */
    List<Repository> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Checks if a repository with the given GitHub URL already exists.
     */
    boolean existsByGithubUrl(String githubUrl);

    /**
     * Checks if a repository with the given GitHub URL already exists for a specific user.
     */
    boolean existsByGithubUrlAndUserId(String githubUrl, UUID userId);

    /**
     * Finds a repository by ID and user ID.
     */
    Optional<Repository> findByIdAndUserId(UUID id, UUID userId);
}
