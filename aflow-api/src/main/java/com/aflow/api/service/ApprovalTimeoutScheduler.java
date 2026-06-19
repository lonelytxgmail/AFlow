package com.aflow.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批超时定时任务。
 * <p>
 * 每分钟扫描一次过期的审批请求，按 timeoutAction 配置自动处理。
 */
@Component
public class ApprovalTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTimeoutScheduler.class);

    private final ApprovalService approvalService;

    public ApprovalTimeoutScheduler(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 每分钟执行一次，扫描过期审批请求并自动处理。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void checkExpiredApprovals() {
        try {
            approvalService.processExpiredRequests();
        } catch (Exception e) {
            log.error("Error processing expired approval requests", e);
        }
    }
}
