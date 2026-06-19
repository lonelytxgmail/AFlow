package com.aflow.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AFlow Agent/LLM 级 Micrometer 指标采集。
 * <p>
 * 暴露以下指标：
 * <ul>
 *   <li>{@code aflow.agent.iterations} — Agent ReAct 迭代次数分布</li>
 *   <li>{@code aflow.agent.tool.calls} — Agent Tool 调用计数（按 toolName/status 分标签）</li>
 *   <li>{@code aflow.llm.calls} — LLM 调用总次数（按 provider/status 分标签）</li>
 *   <li>{@code aflow.llm.duration} — LLM 调用延迟（按 provider 分标签）</li>
 *   <li>{@code aflow.llm.tokens.estimated} — 估算 token 消耗（按 direction 分标签）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.1.0
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次 Agent 执行完成后的迭代次数。
     */
    public void recordAgentIterations(int iterations) {
        DistributionSummary.builder("aflow.agent.iterations")
                .description("Agent ReAct iteration count distribution")
                .register(registry)
                .record(iterations);
    }

    /**
     * 记录一次 Tool 调用。
     *
     * @param toolName Tool 名称
     * @param status   "success", "failed", "timeout"
     */
    public void recordToolCall(String toolName, String status) {
        Counter.builder("aflow.agent.tool.calls")
                .tag("toolName", toolName)
                .tag("status", status)
                .description("Agent tool call count")
                .register(registry)
                .increment();
    }

    /**
     * 记录一次 LLM 调用。
     *
     * @param provider  提供商标识（如 "spring-ai"）
     * @param status    "success" 或 "failed"
     * @param latencyMs 调用延迟（毫秒）
     */
    public void recordLlmCall(String provider, String status, long latencyMs) {
        Counter.builder("aflow.llm.calls")
                .tag("provider", provider)
                .tag("status", status)
                .description("LLM call count")
                .register(registry)
                .increment();

        Timer.builder("aflow.llm.duration")
                .tag("provider", provider)
                .description("LLM call duration")
                .register(registry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 token 消耗估算。
     *
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     */
    public void recordTokenUsage(int promptTokens, int completionTokens) {
        Counter.builder("aflow.llm.tokens.estimated")
                .tag("direction", "prompt")
                .description("Estimated token consumption")
                .register(registry)
                .increment(promptTokens);

        Counter.builder("aflow.llm.tokens.estimated")
                .tag("direction", "completion")
                .description("Estimated token consumption")
                .register(registry)
                .increment(completionTokens);
    }
}
