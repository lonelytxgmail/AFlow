package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.service.ApprovalService;
import com.aflow.persistence.entity.ApprovalRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批 API — 人工审批节点的操作接口。
 * <p>
 * 提供待审批列表查询、审批详情获取、批准/拒绝操作。
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * GET /api/v1/approvals — 待审批列表（支持 status 筛选）。
     */
    @GetMapping
    public ApiResponse<List<ApprovalRequestEntity>> list(
            @RequestParam(required = false) String status) {
        log.info("Listing approval requests, status filter={}", status);
        List<ApprovalRequestEntity> list;
        if ("PENDING".equalsIgnoreCase(status)) {
            list = approvalService.listPending();
        } else {
            list = approvalService.listAll();
        }
        return ApiResponse.ok(list);
    }

    /**
     * GET /api/v1/approvals/{id} — 审批详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ApprovalRequestEntity> getById(@PathVariable String id) {
        log.info("Getting approval request: id={}", id);
        ApprovalRequestEntity entity = approvalService.getById(id);
        return ApiResponse.ok(entity);
    }

    /**
     * POST /api/v1/approvals/{id}/approve — 批准审批。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalRequestEntity> approve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        log.info("Approving request: id={}", id);
        Map<String, Object> data = body != null ? (Map<String, Object>) body.get("data") : null;
        ApprovalRequestEntity result = approvalService.approve(id, data);
        return ApiResponse.ok(result);
    }

    /**
     * POST /api/v1/approvals/{id}/reject — 拒绝审批。
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalRequestEntity> reject(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        log.info("Rejecting request: id={}", id);
        String reason = body != null ? (String) body.get("reason") : null;
        ApprovalRequestEntity result = approvalService.reject(id, reason);
        return ApiResponse.ok(result);
    }
}
