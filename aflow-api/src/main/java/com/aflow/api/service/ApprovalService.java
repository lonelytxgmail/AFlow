package com.aflow.api.service;

import com.aflow.core.engine.WorkflowEngine;
import com.aflow.core.event.FlowEventBus;
import com.aflow.persistence.entity.ApprovalRequestEntity;
import com.aflow.persistence.repository.ApprovalRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 审批业务逻辑服务。
 * <p>
 * 负责创建审批请求、处理审批/拒绝操作、查询待审批列表，
 * 以及触发流程恢复或取消。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRequestRepository approvalRepository;
    private final WorkflowEngine workflowEngine;
    private final FlowEventBus flowEventBus;
    private final ObjectMapper objectMapper;

    public ApprovalService(ApprovalRequestRepository approvalRepository,
                           WorkflowEngine workflowEngine,
                           FlowEventBus flowEventBus,
                           ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.workflowEngine = workflowEngine;
        this.flowEventBus = flowEventBus;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建审批请求（由引擎执行审批节点后调用）。
     */
    @Transactional
    public ApprovalRequestEntity createRequest(String flowId, String nodeId, Map<String, Object> config) {
        ApprovalRequestEntity entity = new ApprovalRequestEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setFlowId(flowId);
        entity.setNodeId(nodeId);
        entity.setTitle(getStringOrDefault(config, "title", "Approval Required"));
        entity.setDescription(getStringOrDefault(config, "description", ""));

        Object options = config.get("options");
        if (options != null) {
            try {
                entity.setOptions(objectMapper.writeValueAsString(options));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize approval options", e);
            }
        }

        int timeoutMinutes = getIntOrDefault(config, "timeoutMinutes", 1440);
        entity.setDeadline(LocalDateTime.now().plusMinutes(timeoutMinutes));
        entity.setTimeoutAction(getStringOrDefault(config, "timeoutAction", "reject"));
        entity.setStatus("PENDING");

        approvalRepository.save(entity);

        // Publish APPROVAL_REQUESTED event
        publishApprovalEvent(entity, "APPROVAL_REQUESTED");

        log.info("Created approval request: id={}, flowId={}, nodeId={}, deadline={}",
                entity.getId(), flowId, nodeId, entity.getDeadline());
        return entity;
    }

    /**
     * 审批通过 → 触发流程恢复。
     */
    @Transactional
    public ApprovalRequestEntity approve(String requestId, Map<String, Object> data) {
        ApprovalRequestEntity entity = findByIdOrThrow(requestId);
        assertPending(entity);

        entity.setStatus("APPROVED");
        if (data != null) {
            try {
                entity.setApprovalData(objectMapper.writeValueAsString(data));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize approval data", e);
            }
        }
        approvalRepository.save(entity);

        // Resume the flow with approval data
        Map<String, Object> resumeInputs = new HashMap<>();
        resumeInputs.put("_approvalResult", "APPROVED");
        if (data != null) {
            resumeInputs.put("_approvalData", data);
        }
        workflowEngine.resume(entity.getFlowId(), resumeInputs);

        // Publish APPROVAL_COMPLETED event
        publishApprovalEvent(entity, "APPROVAL_COMPLETED");

        log.info("Approval approved: id={}, flowId={}", requestId, entity.getFlowId());
        return entity;
    }

    /**
     * 审批拒绝 → 触发流程走 error 边或终止。
     */
    @Transactional
    public ApprovalRequestEntity reject(String requestId, String reason) {
        ApprovalRequestEntity entity = findByIdOrThrow(requestId);
        assertPending(entity);

        entity.setStatus("REJECTED");
        entity.setRejectReason(reason);
        approvalRepository.save(entity);

        // Resume the flow with rejection info — engine handles error routing
        Map<String, Object> resumeInputs = new HashMap<>();
        resumeInputs.put("_approvalResult", "REJECTED");
        resumeInputs.put("_approvalReason", reason != null ? reason : "");
        workflowEngine.resume(entity.getFlowId(), resumeInputs);

        // Publish APPROVAL_COMPLETED event
        publishApprovalEvent(entity, "APPROVAL_COMPLETED");

        log.info("Approval rejected: id={}, flowId={}, reason={}", requestId, entity.getFlowId(), reason);
        return entity;
    }

    /**
     * 查询待审批列表。
     */
    public List<ApprovalRequestEntity> listPending() {
        return approvalRepository.findByStatus("PENDING");
    }

    /**
     * 查询所有审批请求。
     */
    public List<ApprovalRequestEntity> listAll() {
        return approvalRepository.findAll();
    }

    /**
     * 根据 ID 获取审批详情。
     */
    public ApprovalRequestEntity getById(String id) {
        return findByIdOrThrow(id);
    }

    /**
     * 处理超时审批请求（定时任务调用）。
     */
    @Transactional
    public void processExpiredRequests() {
        List<ApprovalRequestEntity> expired = approvalRepository
                .findByStatusAndDeadlineBefore("PENDING", LocalDateTime.now());

        for (ApprovalRequestEntity entity : expired) {
            log.info("Processing expired approval: id={}, action={}", entity.getId(), entity.getTimeoutAction());

            switch (entity.getTimeoutAction() != null ? entity.getTimeoutAction() : "reject") {
                case "approve" -> {
                    entity.setStatus("APPROVED");
                    approvalRepository.save(entity);
                    Map<String, Object> resumeInputs = Map.of(
                            "_approvalResult", "APPROVED",
                            "_approvalTimeout", true
                    );
                    workflowEngine.resume(entity.getFlowId(), resumeInputs);
                }
                case "reject" -> {
                    entity.setStatus("TIMEOUT");
                    approvalRepository.save(entity);
                    Map<String, Object> resumeInputs = Map.of(
                            "_approvalResult", "TIMEOUT",
                            "_approvalReason", "Approval request timed out"
                    );
                    workflowEngine.resume(entity.getFlowId(), resumeInputs);
                }
                case "escalate" -> {
                    // Extend deadline by another period and publish timeout event
                    entity.setDeadline(entity.getDeadline().plusHours(24));
                    approvalRepository.save(entity);
                    log.info("Escalated approval: id={}, new deadline={}", entity.getId(), entity.getDeadline());
                }
                default -> {
                    entity.setStatus("TIMEOUT");
                    approvalRepository.save(entity);
                }
            }

            // Publish APPROVAL_TIMEOUT event
            publishApprovalEvent(entity, "APPROVAL_TIMEOUT");
        }
    }

    // --- Private helpers ---

    private ApprovalRequestEntity findByIdOrThrow(String id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found: " + id));
    }

    private void assertPending(ApprovalRequestEntity entity) {
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Approval request is not pending (current status: " + entity.getStatus() + ")");
        }
    }

    private void publishApprovalEvent(ApprovalRequestEntity entity, String eventName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("approvalRequestId", entity.getId());
        eventData.put("flowId", entity.getFlowId());
        eventData.put("nodeId", entity.getNodeId());
        eventData.put("title", entity.getTitle());
        eventData.put("status", entity.getStatus());
        if (entity.getDeadline() != null) {
            eventData.put("deadline", entity.getDeadline().toString());
        }
        flowEventBus.publish(entity.getFlowId(), eventName, eventData);
    }

    private String getStringOrDefault(Map<String, Object> cfg, String key, String defaultValue) {
        Object value = cfg.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int getIntOrDefault(Map<String, Object> cfg, String key, int defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
