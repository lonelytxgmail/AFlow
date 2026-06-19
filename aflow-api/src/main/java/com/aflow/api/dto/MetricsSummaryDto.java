package com.aflow.api.dto;

import java.util.List;

/**
 * 监控面板聚合指标快照 DTO。
 * <p>
 * 对应 GET /api/v1/metrics/summary 响应 data 部分。
 */
public record MetricsSummaryDto(
        int activeFlows,
        long todayExecutions,
        double successRate,
        double avgDurationMs,
        AgentMetricsSummary agent,
        LlmMetricsSummary llm,
        List<NodeDurationSummary> nodeDurations
) {

    public record AgentMetricsSummary(
            long totalTokens,
            double avgIterations,
            List<ToolUsage> topTools
    ) {}

    public record LlmMetricsSummary(
            long totalCalls,
            double p50LatencyMs,
            double retryRate
    ) {}

    public record NodeDurationSummary(
            String nodeType,
            double avgDurationMs,
            double p95DurationMs,
            long count
    ) {}

    public record ToolUsage(
            String tool,
            long count
    ) {}
}
