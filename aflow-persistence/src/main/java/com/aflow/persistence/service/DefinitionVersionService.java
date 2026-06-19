package com.aflow.persistence.service;

import com.aflow.persistence.entity.DefinitionVersionEntity;
import com.aflow.persistence.entity.FlowDefinitionEntity;
import com.aflow.persistence.repository.DefinitionVersionRepository;
import com.aflow.persistence.repository.FlowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing definition version snapshots.
 * Handles creating version snapshots on publish and rollback operations.
 */
@Service
@Transactional
public class DefinitionVersionService {

    private static final Logger log = LoggerFactory.getLogger(DefinitionVersionService.class);

    private final DefinitionVersionRepository versionRepository;
    private final FlowDefinitionRepository definitionRepository;

    public DefinitionVersionService(DefinitionVersionRepository versionRepository,
                                    FlowDefinitionRepository definitionRepository) {
        this.versionRepository = versionRepository;
        this.definitionRepository = definitionRepository;
    }

    /**
     * Create a version snapshot for a definition.
     * Called automatically during publish.
     *
     * @param definitionId the definition ID
     * @return the created version entity
     */
    public DefinitionVersionEntity createVersionSnapshot(String definitionId) {
        FlowDefinitionEntity definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new RuntimeException("Definition not found: " + definitionId));

        int nextVersion = versionRepository.findMaxVersionNumber(definitionId) + 1;
        String snapshotJson = definition.getDslContent();

        DefinitionVersionEntity version = new DefinitionVersionEntity(definitionId, nextVersion, snapshotJson);
        DefinitionVersionEntity saved = versionRepository.save(version);

        log.info("Created version snapshot: definitionId={}, version={}", definitionId, nextVersion);
        return saved;
    }

    /**
     * List all versions for a definition (newest first).
     */
    @Transactional(readOnly = true)
    public List<DefinitionVersionEntity> listVersions(String definitionId) {
        return versionRepository.findByDefinitionIdOrderByVersionNumberDesc(definitionId);
    }

    /**
     * Get a specific version.
     */
    @Transactional(readOnly = true)
    public Optional<DefinitionVersionEntity> getVersion(String definitionId, int versionNumber) {
        return versionRepository.findByDefinitionIdAndVersionNumber(definitionId, versionNumber);
    }

    /**
     * Rollback a definition to a specific version.
     * Creates a new version with the content of the target version.
     *
     * @param definitionId  the definition ID
     * @param versionNumber the version to rollback to
     * @return the newly created version entity (with the rolled-back content)
     */
    public DefinitionVersionEntity rollback(String definitionId, int versionNumber) {
        DefinitionVersionEntity targetVersion = versionRepository
                .findByDefinitionIdAndVersionNumber(definitionId, versionNumber)
                .orElseThrow(() -> new RuntimeException(
                        "Version not found: definitionId=" + definitionId + ", version=" + versionNumber));

        // Update the definition's DSL content to the target version
        FlowDefinitionEntity definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new RuntimeException("Definition not found: " + definitionId));
        definition.setDslContent(targetVersion.getSnapshotJson());
        definitionRepository.save(definition);

        // Create a new version with the rolled-back content
        int newVersion = versionRepository.findMaxVersionNumber(definitionId) + 1;
        DefinitionVersionEntity rollbackVersion = new DefinitionVersionEntity(
                definitionId, newVersion, targetVersion.getSnapshotJson());
        DefinitionVersionEntity saved = versionRepository.save(rollbackVersion);

        log.info("Rolled back: definitionId={}, from version={} to new version={}",
                definitionId, versionNumber, newVersion);
        return saved;
    }
}
