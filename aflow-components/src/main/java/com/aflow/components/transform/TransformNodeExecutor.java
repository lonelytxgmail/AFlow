package com.aflow.components.transform;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;

@FlowNode(type = "transform", name = "Data Transform", description = "Transform data using SpEL mapping expressions")
public class TransformNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransformNodeExecutor.class);
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        Object mappingObj = cfg.get("mapping");

        if (!(mappingObj instanceof Map<?, ?> mapping)) {
            return NodeResult.failed("Transform config must have a 'mapping' map");
        }

        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        context.getVariables().forEach(evalContext::setVariable);

        Map<String, Object> outputs = new HashMap<>();
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String expr = String.valueOf(entry.getValue());
            try {
                Object result = parser.parseExpression(expr).getValue(evalContext);
                outputs.put(key, result);
            } catch (Exception e) {
                log.warn("Transform mapping '{}' failed: {}", key, e.getMessage());
                outputs.put(key, null);
            }
        }

        log.info("Transform completed: {} mappings", outputs.size());
        return NodeResult.success(outputs);
    }
}
