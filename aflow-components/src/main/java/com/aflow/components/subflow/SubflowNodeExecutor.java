package com.aflow.components.subflow;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.engine.FlowInstancePersistenceService;
import com.aflow.core.engine.WorkflowEngine;
import com.aflow.core.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 子流程节点执行器。
 * <p>
 * 在一个 workflow 中调用另一个完整的 workflow 作为子流程。
 * 支持同步等待子流程完成并映射输出，或异步启动后立即返回。
 * <p>
 * <b>配置参数（NodeConfig.config）：</b>
 * <ul>
 *   <li><code>definitionId</code> — 子流程定义 ID（必填）</li>
 *   <li><code>inputs</code> — 传入子流程的输入参数映射，值支持 ${#varName} 模板（可选）</li>
 *   <li><code>outputMapping</code> — 子流程输出变量到父流程变量的映射（可选）</li>
 *   <li><code>waitForCompletion</code> — 是否同步等待子流程完成（默认 true）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.1.0
 */
@Component
@FlowNode(type = "subflow", name = "子流程", description = "调用另一个完整的 workflow 作为子流程执行")
public class SubflowNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SubflowNodeExecutor.class);

    private final ObjectProvider<WorkflowEngine> workflowEngineProvider;
    private final ObjectProvider<FlowInstancePersistenceService> instancePersistenceProvider;
    private final ExpressionEvaluator expressionEvaluator;

    public SubflowNodeExecutor(ObjectProvider<WorkflowEngine> workflowEngineProvider,
                               ObjectProvider<FlowInstancePersistenceService> instancePersistenceProvider,
                               ExpressionEvaluator expressionEvaluator) {
        this.workflowEngineProvider = workflowEngineProvider;
        this.instancePersistenceProvider = instancePersistenceProvider;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        if (cfg == null || !cfg.containsKey("definitionId")) {
            return NodeResult.failed("子流程节点缺少 definitionId 配置");
        }

        String definitionId = String.valueOf(cfg.get("definitionId"));
        boolean waitForCompletion = cfg.get("waitForCompletion") == null
                || Boolean.parseBoolean(String.valueOf(cfg.get("waitForCompletion")));

        log.info("子流程启动: definitionId={}, waitForCompletion={}", definitionId, waitForCompletion);

        // 1. 解析输入参数（支持模板变量）
        Map<String, Object> inputs = resolveInputs(cfg, context);

        // 2. 启动子流程
        WorkflowEngine engine = workflowEngineProvider.getObject();
        FlowContext childContext;
        try {
            childContext = engine.start(definitionId, inputs);
        } catch (Exception e) {
            log.error("子流程启动失败: definitionId={}, error={}", definitionId, e.getMessage());
            return NodeResult.failed("子流程启动失败: " + e.getMessage());
        }

        String childInstanceId = childContext.getFlowInstanceId();
        log.info("子流程已启动: childInstanceId={}, parentInstanceId={}", childInstanceId, context.getFlowInstanceId());

        // 3. 如果不等待完成，直接返回子流程实例 ID
        if (!waitForCompletion) {
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("childInstanceId", childInstanceId);
            outputs.put("childStatus", childContext.getStatus().name());
            return NodeResult.success(outputs);
        }

        // 4. 同步等待子流程完成（利用虚拟线程轮询）
        FlowInstancePersistenceService instancePersistence = instancePersistenceProvider.getObject();
        FlowContext finalChildContext = waitForChildCompletion(childInstanceId, instancePersistence);

        if (finalChildContext == null) {
            return NodeResult.failed("子流程等待超时或不存在: childInstanceId=" + childInstanceId);
        }

        // 5. 根据子流程状态返回结果
        if (finalChildContext.getStatus() == FlowStatus.COMPLETED) {
            Map<String, Object> outputs = buildOutputs(cfg, finalChildContext);
            outputs.put("childInstanceId", childInstanceId);
            outputs.put("childStatus", "COMPLETED");
            log.info("子流程完成: childInstanceId={}", childInstanceId);
            return NodeResult.success(outputs);
        } else if (finalChildContext.getStatus() == FlowStatus.FAILED) {
            log.warn("子流程失败: childInstanceId={}", childInstanceId);
            return NodeResult.failed("子流程执行失败: childInstanceId=" + childInstanceId);
        } else if (finalChildContext.getStatus() == FlowStatus.CANCELLED) {
            return NodeResult.failed("子流程已取消: childInstanceId=" + childInstanceId);
        } else {
            // SUSPENDED or other state — should not happen in sync mode
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("childInstanceId", childInstanceId);
            outputs.put("childStatus", finalChildContext.getStatus().name());
            return NodeResult.success(outputs);
        }
    }

    /**
     * 解析 inputs 配置中的模板变量。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInputs(Map<String, Object> cfg, FlowContext context) {
        Object inputsConfig = cfg.get("inputs");
        if (inputsConfig == null) {
            return new HashMap<>();
        }
        if (!(inputsConfig instanceof Map)) {
            return new HashMap<>();
        }

        Map<String, Object> inputs = new HashMap<>();
        Map<String, Object> inputMap = (Map<String, Object>) inputsConfig;
        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strVal && strVal.contains("${")) {
                value = expressionEvaluator.resolveTemplate(strVal, context);
            }
            inputs.put(entry.getKey(), value);
        }
        return inputs;
    }

    /**
     * 等待子流程完成（轮询，虚拟线程安全）。
     */
    private FlowContext waitForChildCompletion(String childInstanceId,
                                               FlowInstancePersistenceService instancePersistence) {
        long maxWaitMs = 300_000; // 5 minutes max wait
        long pollIntervalMs = 100;
        long waited = 0;

        while (waited < maxWaitMs) {
            FlowContext child = instancePersistence.findById(childInstanceId).orElse(null);
            if (child == null) return null;

            FlowStatus status = child.getStatus();
            if (status == FlowStatus.COMPLETED || status == FlowStatus.FAILED || status == FlowStatus.CANCELLED) {
                return child;
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return child;
            }
            waited += pollIntervalMs;
        }

        log.warn("子流程等待超时: childInstanceId={}, maxWaitMs={}", childInstanceId, maxWaitMs);
        return instancePersistence.findById(childInstanceId).orElse(null);
    }

    /**
     * 根据 outputMapping 配置从子流程上下文中提取输出。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildOutputs(Map<String, Object> cfg, FlowContext childContext) {
        Map<String, Object> outputs = new HashMap<>();
        Object outputMappingConfig = cfg.get("outputMapping");

        if (outputMappingConfig instanceof Map) {
            Map<String, Object> mapping = (Map<String, Object>) outputMappingConfig;
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                String childVarName = entry.getKey();
                String parentVarName = String.valueOf(entry.getValue());
                Object childValue = childContext.getVariables().get(childVarName);
                outputs.put(parentVarName, childValue);
            }
        } else {
            // 无映射配置时，返回子流程所有变量
            outputs.putAll(childContext.getVariables());
        }

        return outputs;
    }
}
