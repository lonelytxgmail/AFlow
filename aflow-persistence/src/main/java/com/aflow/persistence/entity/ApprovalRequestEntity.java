package com.aflow.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a human approval request.
 * <p>
 * Created when an approval node executes; remains in PENDING state
 * until a human approves/rejects or the deadline expires.
 */
@Entity
@Table(name = "approval_request", indexes = {
    @Index(name = "idx_approval_flow_id", columnList = "flow_id"),
    @Index(name = "idx_approval_status", columnList = "status"),
    @Index(name = "idx_approval_deadline", columnList = "deadline")
})
public class ApprovalRequestEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "flow_id", nullable = false, length = 64)
    private String flowId;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** JSON array of approval options, e.g. [{"label":"Approve","value":"approve"}] */
    @Column(columnDefinition = "JSON")
    private String options;

    /** PENDING | APPROVED | REJECTED | TIMEOUT */
    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column
    private LocalDateTime deadline;

    /** Action to take on timeout: approve | reject | escalate */
    @Column(name = "timeout_action", length = 32)
    private String timeoutAction = "reject";

    /** Data submitted by the approver (JSON) */
    @Column(name = "approval_data", columnDefinition = "JSON")
    private String approvalData;

    /** Reason for rejection */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ApprovalRequestEntity() {
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

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public String getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(String timeoutAction) {
        this.timeoutAction = timeoutAction;
    }

    public String getApprovalData() {
        return approvalData;
    }

    public void setApprovalData(String approvalData) {
        this.approvalData = approvalData;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
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
