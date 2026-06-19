package com.aflow.components.condition;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@FlowNode(type = "condition", name = "Condition Router", description = "Route based on SpEL condition")
public class ConditionNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConditionNodeExecutor.class);
    private final ExpressionEvaluator expressionEvaluator;

    public ConditionNodeExecutor(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        String expression = String.valueOf(cfg.getOrDefault("expression", "false"));
        String trueNext = cfg.get("trueNext") != null ? String.valueOf(cfg.get("trueNext")) : null;
        String falseNext = cfg.get("falseNext") != null ? String.valueOf(cfg.get("falseNext")) : null;

        try {
            Boolean result = expressionEvaluator.evaluate(expression, context, Boolean.class);
            String nextNode = Boolean.TRUE.equals(result) ? trueNext : falseNext;

            log.info("Condition '{}' evaluated to {}, next node: {}", expression, result, nextNode);
            return NodeResult.successWithNext(Map.of("conditionResult", Boolean.TRUE.equals(result)), nextNode);
        } catch (Exception e) {
            log.error("Condition evaluation failed: {}", e.getMessage());
            return NodeResult.failed("Condition evaluation failed: " + e.getMessage());
        }
    }
}
