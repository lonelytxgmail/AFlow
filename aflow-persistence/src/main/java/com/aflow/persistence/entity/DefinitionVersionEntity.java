package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a version snapshot of a flow definition.
 * Created automatically on each publish operation.
 */
@Entity
@Table(name = "definition_version",
       uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "version_number"}))
public class DefinitionVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false, length = 64)
    private String definitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "snapshot_json", columnDefinition = "JSON", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DefinitionVersionEntity() {
    }

    public DefinitionVersionEntity(String definitionId, int versionNumber, String snapshotJson) {
        this.definitionId = definitionId;
        this.versionNumber = versionNumber;
        this.snapshotJson = snapshotJson;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
