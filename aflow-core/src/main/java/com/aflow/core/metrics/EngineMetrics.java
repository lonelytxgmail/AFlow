package com.aflow.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AFlow 引擎级 Micrometer 指标采集。
 * <p>
 * 暴露以下指标：
 * <ul>
 *   <li>{@code aflow.node.execution.duration} — 节点执行耗时（Timer，按 nodeType/status 分标签）</li>
 *   <li>{@code aflow.node.execution.total} — 节点执行总次数（Counter，按 nodeType/status 分标签）</li>
 *   <li>{@code aflow.flow.active} — 当前活跃流程实例数（Gauge）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.1.0
 */
@Component
public class EngineMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeFlows = new AtomicInteger(0);

    public EngineMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("aflow.flow.active", activeFlows, AtomicInteger::get)
                .description("Number of currently active flow instances")
                .register(registry);
    }

    /**
     * 记录一次节点执行。
     *
     * @param nodeType   节点类型（如 "http", "agent", "script"）
     * @param status     执行结果状态（"SUCCESS", "FAILED", "SUSPENDED"）
     * @param durationMs 执行耗时（毫秒）
     */
    public void recordNodeExecution(String nodeType, String status, long durationMs) {
        Timer.builder("aflow.node.execution.duration")
                .tag("nodeType", nodeType)
                .tag("status", status)
                .description("Node execution duration")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        Counter.builder("aflow.node.execution.total")
                .tag("nodeType", nodeType)
                .tag("status", status)
                .description("Total node executions")
                .register(registry)
                .increment();
    }

    /**
     * 流程启动时调用。
     */
    public void flowStarted() {
        activeFlows.incrementAndGet();
    }

    /**
     * 流程结束（完成/失败/取消）时调用。
     */
    public void flowEnded() {
        activeFlows.decrementAndGet();
    }
}
