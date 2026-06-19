package com.aflow.components.assign;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.expression.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@FlowNode(type = "assign", name = "Assign Variable", description = "Assign values to context variables")
public class AssignNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AssignNodeExecutor.class);
    private final ExpressionEvaluator expressionEvaluator;

    public AssignNodeExecutor(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Object assignmentsObj = config.getConfig().get("assignments");
        if (!(assignmentsObj instanceof Map<?, ?> assignments)) {
            return NodeResult.failed("Assign config must have an 'assignments' map");
        }

        Map<String, Object> outputs = new HashMap<>();
        for (Map.Entry<?, ?> entry : assignments.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof String strValue && strValue.startsWith("#")) {
                try {
                    value = expressionEvaluator.evaluate(strValue, context, Object.class);
                } catch (Exception e) {
                    log.warn("SpEL evaluation failed for '{}': {}", strValue, e.getMessage());
                }
            }
            outputs.put(key, value);
        }

        log.info("Assigned {} variables", outputs.size());
        return NodeResult.success(outputs);
    }
}
