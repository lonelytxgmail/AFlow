package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a running or completed flow instance.
 * Tracks execution status, current node, context variables, and metadata.
 */
@Entity
@Table(name = "flow_instance", indexes = {
    @Index(name = "idx_definition_id", columnList = "definition_id"),
    @Index(name = "idx_status", columnList = "status")
})
public class FlowInstanceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "definition_id", nullable = false, length = 64)
    private String definitionId;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion = 1;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "current_node_id", length = 128)
    private String currentNodeId;

    @Column(columnDefinition = "JSON")
    private String variables;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "trigger_type", length = 32)
    private String triggerType = "MANUAL";

    @Column(name = "execution_path", columnDefinition = "JSON")
    private String executionPath;

    @Column(name = "breakpoints", columnDefinition = "JSON")
    private String breakpoints;

    @Column(name = "debug_mode")
    private boolean debugMode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FlowInstanceEntity() {
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

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(int definitionVersion) {
        this.definitionVersion = definitionVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getExecutionPath() {
        return executionPath;
    }

    public void setExecutionPath(String executionPath) {
        this.executionPath = executionPath;
    }

    public String getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(String breakpoints) {
        this.breakpoints = breakpoints;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
