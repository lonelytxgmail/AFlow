package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a flow execution event.
 * Tracks node lifecycle events (enter, exit, error) with optional timing data.
 */
@Entity
@Table(name = "flow_event", indexes = {
    @Index(name = "idx_event_flow_instance_id", columnList = "flow_instance_id")
})
public class FlowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_instance_id", nullable = false, length = 64)
    private String flowInstanceId;

    @Column(name = "node_id", length = 128)
    private String nodeId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "event_data", columnDefinition = "JSON")
    private String eventData;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FlowEventEntity() {
    }

    public FlowEventEntity(String flowInstanceId, String nodeId, String eventType,
                           String eventData, Long durationMs) {
        this.flowInstanceId = flowInstanceId;
        this.nodeId = nodeId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.durationMs = durationMs;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventData() {
        return eventData;
    }

    public void setEventData(String eventData) {
        this.eventData = eventData;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
