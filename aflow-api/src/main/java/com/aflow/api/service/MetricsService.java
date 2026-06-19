package com.aflow.api.service;

import com.aflow.api.dto.MetricsSummaryDto;
import com.aflow.api.dto.MetricsSummaryDto.AgentMetricsSummary;
import com.aflow.api.dto.MetricsSummaryDto.LlmMetricsSummary;
import com.aflow.api.dto.MetricsSummaryDto.NodeDurationSummary;
import com.aflow.api.dto.MetricsSummaryDto.ToolUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 聚合 Micrometer MeterRegistry 中的指标，返回监控面板快照。
 * <p>
 * 查询的指标来源：
 * <ul>
 *   <li>{@code aflow.flow.active} (Gauge) — 活跃流程数</li>
 *   <li>{@code aflow.node.execution.total} (Counter) — 节点执行总次数</li>
 *   <li>{@code aflow.node.execution.duration} (Timer) — 节点执行耗时</li>
 *   <li>{@code aflow.agent.iterations} (DistributionSummary) — Agent 迭代次数</li>
 *   <li>{@code aflow.agent.tool.calls} (Counter) — Tool 调用计数</li>
 *   <li>{@code aflow.llm.calls} (Counter) — LLM 调用次数</li>
 *   <li>{@code aflow.llm.duration} (Timer) — LLM 调用延迟</li>
 *   <li>{@code aflow.llm.tokens.estimated} (Counter) — Token 消耗估算</li>
 * </ul>
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 构建聚合指标快照。
     */
    public MetricsSummaryDto buildSummary() {
        int activeFlows = getActiveFlows();
        long todayExecutions = getTodayExecutions();
        double successRate = computeSuccessRate();
        double avgDurationMs = computeAvgDurationMs();

        AgentMetricsSummary agent = buildAgentSummary();
        LlmMetricsSummary llm = buildLlmSummary();
        List<NodeDurationSummary> nodeDurations = buildNodeDurations();

        return new MetricsSummaryDto(
                activeFlows, todayExecutions, successRate, avgDurationMs,
                agent, llm, nodeDurations
        );
    }

    private int getActiveFlows() {
        Gauge gauge = registry.find("aflow.flow.active").gauge();
        return gauge != null ? (int) gauge.value() : 0;
    }

    private long getTodayExecutions() {
        // Sum all node execution counters across all nodeType/status tags
        // This gives total node executions; a better proxy for "flow executions" would be
        // counting flow start events, but we use the available counters.
        Collection<Counter> counters = registry.find("aflow.node.execution.total").counters();
        return counters.stream().mapToLong(c -> (long) c.count()).sum();
    }

    private double computeSuccessRate() {
        Collection<Counter> counters = registry.find("aflow.node.execution.total").counters();
        long total = 0;
        long success = 0;
        for (Counter c : counters) {
            long count = (long) c.count();
            total += count;
            if ("SUCCESS".equalsIgnoreCase(c.getId().getTag("status"))) {
                success += count;
            }
        }
        return total > 0 ? (double) success / total : 1.0;
    }

    private double computeAvgDurationMs() {
        Collection<Timer> timers = registry.find("aflow.node.execution.duration").timers();
        long totalCount = 0;
        double totalTime = 0;
        for (Timer t : timers) {
            totalCount += t.count();
            totalTime += t.totalTime(TimeUnit.MILLISECONDS);
        }
        return totalCount > 0 ? totalTime / totalCount : 0;
    }

    private AgentMetricsSummary buildAgentSummary() {
        // Total tokens
        Collection<Counter> tokenCounters = registry.find("aflow.llm.tokens.estimated").counters();
        long totalTokens = tokenCounters.stream().mapToLong(c -> (long) c.count()).sum();

        // Average iterations
        DistributionSummary iterSummary = registry.find("aflow.agent.iterations").summary();
        double avgIterations = iterSummary != null && iterSummary.count() > 0
                ? iterSummary.mean()
                : 0;

        // Top tools (by call count, top 5)
        Collection<Counter> toolCounters = registry.find("aflow.agent.tool.calls").counters();
        Map<String, Long> toolCounts = new HashMap<>();
        for (Counter c : toolCounters) {
            String toolName = c.getId().getTag("toolName");
            if (toolName != null) {
                toolCounts.merge(toolName, (long) c.count(), Long::sum);
            }
        }
        List<ToolUsage> topTools = toolCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new ToolUsage(e.getKey(), e.getValue()))
                .toList();

        return new AgentMetricsSummary(totalTokens, avgIterations, topTools);
    }

    private LlmMetricsSummary buildLlmSummary() {
        // Total LLM calls
        Collection<Counter> llmCounters = registry.find("aflow.llm.calls").counters();
        long totalCalls = llmCounters.stream().mapToLong(c -> (long) c.count()).sum();

        // P50 latency from Timer
        Collection<Timer> llmTimers = registry.find("aflow.llm.duration").timers();
        double p50LatencyMs = 0;
        long timerTotalCount = 0;
        double timerTotalTime = 0;
        for (Timer t : llmTimers) {
            timerTotalCount += t.count();
            timerTotalTime += t.totalTime(TimeUnit.MILLISECONDS);
            // Timer percentiles may not be configured; use mean as approximation
        }
        p50LatencyMs = timerTotalCount > 0 ? timerTotalTime / timerTotalCount : 0;

        // Retry rate: failed / total
        long failedCalls = 0;
        for (Counter c : llmCounters) {
            if ("failed".equalsIgnoreCase(c.getId().getTag("status"))) {
                failedCalls += (long) c.count();
            }
        }
        double retryRate = totalCalls > 0 ? (double) failedCalls / totalCalls : 0;

        return new LlmMetricsSummary(totalCalls, p50LatencyMs, retryRate);
    }

    private List<NodeDurationSummary> buildNodeDurations() {
        Collection<Timer> timers = registry.find("aflow.node.execution.duration").timers();

        // Group by nodeType
        Map<String, List<Timer>> byNodeType = timers.stream()
                .collect(Collectors.groupingBy(t -> {
                    String tag = t.getId().getTag("nodeType");
                    return tag != null ? tag : "unknown";
                }));

        List<NodeDurationSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<Timer>> entry : byNodeType.entrySet()) {
            String nodeType = entry.getKey();
            List<Timer> nodeTimers = entry.getValue();

            long count = 0;
            double totalTime = 0;
            double maxDuration = 0;
            for (Timer t : nodeTimers) {
                count += t.count();
                totalTime += t.totalTime(TimeUnit.MILLISECONDS);
                double max = t.max(TimeUnit.MILLISECONDS);
                if (max > maxDuration) {
                    maxDuration = max;
                }
            }

            double avgDuration = count > 0 ? totalTime / count : 0;
            // P95 approximation: use max as upper-bound estimate when percentiles are not configured
            double p95Duration = maxDuration;

            result.add(new NodeDurationSummary(nodeType, avgDuration, p95Duration, count));
        }

        // Sort by count descending
        result.sort(Comparator.comparingLong(NodeDurationSummary::count).reversed());
        return result;
    }
}
