package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a flow definition.
 * Stores the workflow DSL content as JSON and tracks versioning/status.
 */
@Entity
@Table(name = "flow_definition")
public class FlowDefinitionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "dsl_content", columnDefinition = "JSON")
    private String dslContent;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FlowDefinitionEntity() {
    }

    public FlowDefinitionEntity(String id, String name, int version, String dslContent, String status) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.dslContent = dslContent;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDslContent() {
        return dslContent;
    }

    public void setDslContent(String dslContent) {
        this.dslContent = dslContent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
