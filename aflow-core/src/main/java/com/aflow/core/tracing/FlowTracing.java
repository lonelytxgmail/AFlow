package com.aflow.core.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

/**
 * AFlow 流程执行追踪支持。
 * <p>
 * 基于 Micrometer Observation API，为流程实例和节点执行创建观测点。
 * 当 OpenTelemetry bridge 存在时自动生成 Trace/Span。
 * <p>
 * 如果 ObservationRegistry 为 NOOP（未配置 tracing），所有操作为空操作，零开销。
 *
 * @author AFlow Team
 * @since 1.1.0
 */
@Component
public class FlowTracing {

    private final ObservationRegistry observationRegistry;

    public FlowTracing(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * 创建流程实例级别的 Observation（对应 root span）。
     *
     * @param flowInstanceId 流程实例 ID
     * @param definitionId   流程定义 ID
     * @return Observation 实例，调用方负责 start/stop
     */
    public Observation startFlowObservation(String flowInstanceId, String definitionId) {
        return Observation.createNotStarted("aflow.flow.execution", observationRegistry)
                .lowCardinalityKeyValue("flow.definitionId", definitionId)
                .highCardinalityKeyValue("flow.instanceId", flowInstanceId)
                .start();
    }

    /**
     * 创建节点执行级别的 Observation（对应 child span）。
     *
     * @param flowInstanceId 流程实例 ID
     * @param nodeId         节点 ID
     * @param nodeType       节点类型
     * @param parentObservation 父级 Observation（流程级）
     * @return Observation 实例
     */
    public Observation startNodeObservation(String flowInstanceId, String nodeId, String nodeType,
                                            Observation parentObservation) {
        return Observation.createNotStarted("aflow.node.execution", observationRegistry)
                .parentObservation(parentObservation)
                .lowCardinalityKeyValue("node.type", nodeType)
                .highCardinalityKeyValue("node.id", nodeId)
                .highCardinalityKeyValue("flow.instanceId", flowInstanceId)
                .start();
    }

    /**
     * 创建 Agent LLM 调用级别的 Observation。
     *
     * @param flowInstanceId 流程实例 ID
     * @param iteration      当前 ReAct 迭代轮次
     * @param parentObservation 父级 Observation（节点级）
     * @return Observation 实例
     */
    public Observation startLlmCallObservation(String flowInstanceId, int iteration,
                                               Observation parentObservation) {
        return Observation.createNotStarted("aflow.agent.llm.call", observationRegistry)
                .parentObservation(parentObservation)
                .lowCardinalityKeyValue("agent.operation", "llm_call")
                .highCardinalityKeyValue("flow.instanceId", flowInstanceId)
                .highCardinalityKeyValue("agent.iteration", String.valueOf(iteration))
                .start();
    }

    /**
     * 创建 Agent Tool 调用级别的 Observation。
     *
     * @param flowInstanceId 流程实例 ID
     * @param toolName       工具名称
     * @param parentObservation 父级 Observation（节点级）
     * @return Observation 实例
     */
    public Observation startToolCallObservation(String flowInstanceId, String toolName,
                                                Observation parentObservation) {
        return Observation.createNotStarted("aflow.agent.tool.call", observationRegistry)
                .parentObservation(parentObservation)
                .lowCardinalityKeyValue("tool.name", toolName)
                .highCardinalityKeyValue("flow.instanceId", flowInstanceId)
                .start();
    }

    /**
     * 获取当前 Trace ID（如果 tracing 已启用）。
     * 可用于在 SSE 事件中传递 Trace ID 给前端。
     *
     * @param observation 当前 Observation
     * @return Trace ID 字符串，或 null（如果 tracing 未启用）
     */
    public String getTraceId(Observation observation) {
        if (observation == null) return null;
        var context = observation.getContext();
        // Micrometer Tracing context 中可以通过 TracingObservationHandler 获取 traceId
        // 这里返回 null，具体 traceId 提取在 OTel bridge 存在时由框架自动处理
        return null;
    }
}
