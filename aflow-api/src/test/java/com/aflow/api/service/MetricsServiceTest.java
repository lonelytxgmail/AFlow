package com.aflow.api.service;

import com.aflow.api.dto.MetricsSummaryDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private SimpleMeterRegistry registry;
    private MetricsService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new MetricsService(registry);
    }

    @Test
    void buildSummary_emptyRegistry_returnsDefaults() {
        MetricsSummaryDto summary = service.buildSummary();

        assertEquals(0, summary.activeFlows());
        assertEquals(0, summary.todayExecutions());
        assertEquals(1.0, summary.successRate());
        assertEquals(0.0, summary.avgDurationMs());
        assertEquals(0, summary.agent().totalTokens());
        assertEquals(0.0, summary.agent().avgIterations());
        assertTrue(summary.agent().topTools().isEmpty());
        assertEquals(0, summary.llm().totalCalls());
        assertEquals(0.0, summary.llm().p50LatencyMs());
        assertEquals(0.0, summary.llm().retryRate());
        assertTrue(summary.nodeDurations().isEmpty());
    }

    @Test
    void buildSummary_withActiveFlows_returnsGaugeValue() {
        AtomicInteger activeFlows = new AtomicInteger(3);
        Gauge.builder("aflow.flow.active", activeFlows, AtomicInteger::get)
                .register(registry);

        MetricsSummaryDto summary = service.buildSummary();
        assertEquals(3, summary.activeFlows());
    }

    @Test
    void buildSummary_withNodeExecutions_computesCorrectly() {
        // 10 successful executions
        Counter.builder("aflow.node.execution.total")
                .tag("nodeType", "http")
                .tag("status", "SUCCESS")
                .register(registry)
                .increment(10);

        // 2 failed executions
        Counter.builder("aflow.node.execution.total")
                .tag("nodeType", "http")
                .tag("status", "FAILED")
                .register(registry)
                .increment(2);

        // Timer for duration
        Timer timer = Timer.builder("aflow.node.execution.duration")
                .tag("nodeType", "http")
                .tag("status", "SUCCESS")
                .register(registry);
        // Record 10 x 100ms
        for (int i = 0; i < 10; i++) {
            timer.record(100, TimeUnit.MILLISECONDS);
        }

        MetricsSummaryDto summary = service.buildSummary();

        assertEquals(12, summary.todayExecutions());
        // successRate = 10 / 12
        assertEquals(10.0 / 12.0, summary.successRate(), 0.001);
        // avgDuration = 1000ms / 10 = 100ms
        assertEquals(100.0, summary.avgDurationMs(), 0.1);
    }

    @Test
    void buildSummary_agentMetrics_aggregatesCorrectly() {
        // Token usage
        Counter.builder("aflow.llm.tokens.estimated")
                .tag("direction", "prompt")
                .register(registry)
                .increment(100_000);
        Counter.builder("aflow.llm.tokens.estimated")
                .tag("direction", "completion")
                .register(registry)
                .increment(50_000);

        // Agent iterations
        DistributionSummary iterSummary = DistributionSummary.builder("aflow.agent.iterations")
                .register(registry);
        iterSummary.record(3);
        iterSummary.record(4);

        // Tool calls
        Counter.builder("aflow.agent.tool.calls")
                .tag("toolName", "http")
                .tag("status", "success")
                .register(registry)
                .increment(20);
        Counter.builder("aflow.agent.tool.calls")
                .tag("toolName", "script")
                .tag("status", "success")
                .register(registry)
                .increment(5);

        MetricsSummaryDto summary = service.buildSummary();

        assertEquals(150_000, summary.agent().totalTokens());
        assertEquals(3.5, summary.agent().avgIterations(), 0.001);
        assertEquals(2, summary.agent().topTools().size());
        assertEquals("http", summary.agent().topTools().get(0).tool());
        assertEquals(20, summary.agent().topTools().get(0).count());
        assertEquals("script", summary.agent().topTools().get(1).tool());
        assertEquals(5, summary.agent().topTools().get(1).count());
    }

    @Test
    void buildSummary_llmMetrics_aggregatesCorrectly() {
        // LLM calls
        Counter.builder("aflow.llm.calls")
                .tag("provider", "spring-ai")
                .tag("status", "success")
                .register(registry)
                .increment(100);
        Counter.builder("aflow.llm.calls")
                .tag("provider", "spring-ai")
                .tag("status", "failed")
                .register(registry)
                .increment(5);

        // LLM duration
        Timer llmTimer = Timer.builder("aflow.llm.duration")
                .tag("provider", "spring-ai")
                .register(registry);
        for (int i = 0; i < 5; i++) {
            llmTimer.record(800, TimeUnit.MILLISECONDS);
        }

        MetricsSummaryDto summary = service.buildSummary();

        assertEquals(105, summary.llm().totalCalls());
        assertEquals(800.0, summary.llm().p50LatencyMs(), 0.1);
        // retryRate = 5 / 105
        assertEquals(5.0 / 105.0, summary.llm().retryRate(), 0.001);
    }

    @Test
    void buildSummary_nodeDurations_groupsByNodeType() {
        Timer httpTimer = Timer.builder("aflow.node.execution.duration")
                .tag("nodeType", "http")
                .tag("status", "SUCCESS")
                .register(registry);
        httpTimer.record(200, TimeUnit.MILLISECONDS);
        httpTimer.record(400, TimeUnit.MILLISECONDS);

        Timer agentTimer = Timer.builder("aflow.node.execution.duration")
                .tag("nodeType", "agent")
                .tag("status", "SUCCESS")
                .register(registry);
        agentTimer.record(5000, TimeUnit.MILLISECONDS);

        MetricsSummaryDto summary = service.buildSummary();

        assertEquals(2, summary.nodeDurations().size());
        // http has 2 executions, agent has 1
        var httpNode = summary.nodeDurations().stream()
                .filter(n -> n.nodeType().equals("http")).findFirst().orElseThrow();
        assertEquals(300.0, httpNode.avgDurationMs(), 0.1);
        assertEquals(2, httpNode.count());

        var agentNode = summary.nodeDurations().stream()
                .filter(n -> n.nodeType().equals("agent")).findFirst().orElseThrow();
        assertEquals(5000.0, agentNode.avgDurationMs(), 0.1);
        assertEquals(1, agentNode.count());
    }
}
