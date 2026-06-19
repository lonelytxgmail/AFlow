package com.aflow.persistence.repository;

import com.aflow.persistence.entity.DefinitionVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for definition version snapshots.
 */
@Repository
public interface DefinitionVersionRepository extends JpaRepository<DefinitionVersionEntity, Long> {

    /**
     * Find all versions for a definition, ordered by version number descending (newest first).
     */
    List<DefinitionVersionEntity> findByDefinitionIdOrderByVersionNumberDesc(String definitionId);

    /**
     * Find a specific version for a definition.
     */
    Optional<DefinitionVersionEntity> findByDefinitionIdAndVersionNumber(String definitionId, int versionNumber);

    /**
     * Get the max version number for a definition.
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM DefinitionVersionEntity v WHERE v.definitionId = :definitionId")
    int findMaxVersionNumber(String definitionId);
}
