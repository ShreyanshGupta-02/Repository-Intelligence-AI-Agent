package com.github.repository_ai.repository;

import com.github.repository_ai.entity.RepositoryFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RepositoryFile} entities.
 */
public interface RepositoryFileRepository extends JpaRepository<RepositoryFile, UUID> {

    List<RepositoryFile> findAllByRepositoryId(UUID repositoryId);

    long countByRepositoryId(UUID repositoryId);

    void deleteAllByRepositoryId(UUID repositoryId);
}
