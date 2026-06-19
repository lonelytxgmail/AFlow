package com.aflow.components.log;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@FlowNode(type = "log", name = "Log", description = "Log messages at specified level")
public class LogNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LogNodeExecutor.class);

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        String message = String.valueOf(config.getConfig().getOrDefault("message", ""));
        String level = String.valueOf(config.getConfig().getOrDefault("level", "INFO")).toUpperCase();

        // Resolve ${variable} templates
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            message = message.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }

        switch (level) {
            case "ERROR" -> log.error("[FlowLog] {}", message);
            case "WARN" -> log.warn("[FlowLog] {}", message);
            case "DEBUG" -> log.debug("[FlowLog] {}", message);
            default -> log.info("[FlowLog] {}", message);
        }

        return NodeResult.success(Map.of());
    }
}
