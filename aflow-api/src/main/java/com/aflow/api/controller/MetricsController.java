package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.dto.MetricsSummaryDto;
import com.aflow.api.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控面板 API — 提供聚合指标快照。
 * <p>
 * 数据来源：Micrometer MeterRegistry 中的 aflow.* 指标。
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * GET /api/v1/metrics/summary — 返回聚合关键指标快照。
     */
    @GetMapping("/summary")
    public ApiResponse<MetricsSummaryDto> summary() {
        log.debug("Fetching metrics summary");
        MetricsSummaryDto summary = metricsService.buildSummary();
        return ApiResponse.ok(summary);
    }
}
