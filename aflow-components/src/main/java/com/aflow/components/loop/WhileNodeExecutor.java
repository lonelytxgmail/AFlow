package com.aflow.components.loop;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.*;
import com.aflow.core.engine.SubFlowExecutor;
import com.aflow.core.expression.ExpressionEvaluator;
import com.aflow.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * While 循环节点执行器。
 * <p>
 * 当条件为 true 时重复执行子流程，直到条件为 false 或达到最大迭代次数。
 * <p>
 * <b>配置项：</b>
 * <pre>{@code
 *  * {
 *  *   "condition": "#statusCode == 200",   // SpEL 表达式，每轮迭代前评估
 *  *   "maxIterations": 1000,               // 可选：最大迭代次数（默认 1000）
 *  *   "subFlow": {                          // 子流程定义（内嵌的 DAG）
 *  *     "nodes": [...],
 *  *     "edges": [...]
 *  *   }
 *  * }
 *  * }</pre>
 * <p>
 * <b>执行流程：</b>
 * <ol>
 *   <li>评估 condition SpEL 表达式</li>
 *   <li>如果为 true：调用 {@link SubFlowExecutor} 执行子 DAG，然后回到步骤 1</li>
 *   <li>如果为 false：循环结束，返回成功</li>
 *   <li>如果迭代次数达到 maxIterations：返回失败（防止无限循环）</li>
 * </ol>
 * <p>
 * 典型使用场景：轮询 API 直到返回成功、批量处理分页数据等。
 */
@FlowNode(type = "while", name = "While 循环", description = "条件为 true 时重复执行子流程")
@Component
public class WhileNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(WhileNodeExecutor.class);

    /** 默认最大迭代次数，防止无限循环 */
    private static final int DEFAULT_MAX_ITERATIONS = 1000;

    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectProvider<SubFlowExecutor> subFlowExecutorProvider;

    public WhileNodeExecutor(ExpressionEvaluator expressionEvaluator, ObjectProvider<SubFlowExecutor> subFlowExecutorProvider) {
        this.expressionEvaluator = expressionEvaluator;
        this.subFlowExecutorProvider = subFlowExecutorProvider;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();

        // ── 1. 解析配置参数 ──
        String conditionExpression = String.valueOf(cfg.getOrDefault("condition", ""));
        int maxIterations = cfg.get("maxIterations") != null
                ? Integer.parseInt(String.valueOf(cfg.get("maxIterations")))
                : DEFAULT_MAX_ITERATIONS;

        if (conditionExpression.isBlank()) {
            return NodeResult.failed("while 节点缺少 condition 配置（SpEL 表达式）");
        }

        // ── 2. 解析子流程定义 ──
        FlowDefinition subFlow = parseSubFlow(cfg.get("subFlow"));
        if (subFlow == null) {
            return NodeResult.failed("while 节点缺少 subFlow 配置（子流程定义）");
        }

        // ── 3. 循环执行 ──
        String nodeId = context.getCurrentNodeId();
        int iteration = 0;
        log.info("while 循环开始: node={}, condition='{}', maxIterations={}", nodeId, conditionExpression, maxIterations);

        while (true) {
            // 安全检查：迭代次数上限
            if (iteration >= maxIterations) {
                log.error("while 循环达到最大迭代次数: node={}, maxIterations={}", nodeId, maxIterations);
                return NodeResult.failed("while 循环达到最大迭代次数: " + maxIterations);
            }

            // 评估循环条件
            boolean shouldContinue;
            try {
                Boolean result = expressionEvaluator.evaluate(conditionExpression, context, Boolean.class);
                shouldContinue = Boolean.TRUE.equals(result);
            } catch (Exception e) {
                log.error("while 条件评估失败: '{}', error={}", conditionExpression, e.getMessage());
                return NodeResult.failed("while 条件评估失败: " + e.getMessage());
            }

            // 条件为 false，退出循环
            if (!shouldContinue) {
                log.info("while 循环结束: node={}, totalIterations={}", nodeId, iteration);
                break;
            }

            // 条件为 true，执行子流程
            log.debug("while 迭代: node={}, iteration={}", nodeId, iteration);
            subFlowExecutorProvider.getObject().execute(subFlow, context, nodeId, iteration);
            iteration++;
        }

        // ── 4. 返回执行结果 ──
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("whileIterations", iteration);

        log.info("while 循环完成: node={}, totalIterations={}", nodeId, iteration);
        return NodeResult.success(outputs);
    }

    /**
     * 从配置中解析子流程定义。
     * subFlow 可以是一个完整的 FlowDefinition JSON 对象（含 nodes 和 edges）。
     */
    private FlowDefinition parseSubFlow(Object subFlowConfig) {
        if (subFlowConfig == null) return null;
        try {
            String json = JsonUtil.toJson(subFlowConfig);
            FlowDefinition subFlow = JsonUtil.fromJson(json, FlowDefinition.class);
            if (subFlow.getNodes() == null || subFlow.getNodes().isEmpty()) {
                return null;
            }
            return subFlow;
        } catch (Exception e) {
            log.error("解析子流程定义失败: {}", e.getMessage());
            return null;
        }
    }
}
