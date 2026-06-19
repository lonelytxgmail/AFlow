package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a context snapshot taken before or after node execution.
 * Used for debugging, diff comparisons, and audit trails.
 */
@Entity
@Table(name = "context_snapshot", indexes = {
    @Index(name = "idx_flow_instance_id", columnList = "flow_instance_id"),
    @Index(name = "idx_flow_node", columnList = "flow_instance_id, node_id")
})
public class ContextSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_instance_id", nullable = false, length = 64)
    private String flowInstanceId;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(nullable = false, length = 16)
    private String phase;

    @Column(name = "context_data", columnDefinition = "JSON")
    private String contextData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ContextSnapshotEntity() {
    }

    public ContextSnapshotEntity(String flowInstanceId, String nodeId, String phase, String contextData) {
        this.flowInstanceId = flowInstanceId;
        this.nodeId = nodeId;
        this.phase = phase;
        this.contextData = contextData;
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

    public String getFlowInstanceId() {
        return flowInstanceId;
    }

    public void setFlowInstanceId(String flowInstanceId) {
        this.flowInstanceId = flowInstanceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
