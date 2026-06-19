package com.aflow.components.log;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogNodeExecutorTest {

    private final LogNodeExecutor executor = new LogNodeExecutor();

    @Test
    void execute_defaultConfig_returnsSuccess() {
        NodeConfig config = new NodeConfig("log", Map.of(), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
    }

    @Test
    void execute_withMessage_returnsSuccess() {
        NodeConfig config = new NodeConfig("log", Map.of("message", "Hello World", "level", "INFO"), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
    }

    @Test
    void execute_withVariableSubstitution() {
        NodeConfig config = new NodeConfig("log", Map.of("message", "User: ${name}", "level", "DEBUG"), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("name", "Alice");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
    }

    @Test
    void execute_allLogLevels_succeed() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        for (String level : new String[]{"INFO", "WARN", "ERROR", "DEBUG", "UNKNOWN"}) {
            NodeConfig config = new NodeConfig("log", Map.of("message", "test", "level", level), null);
            NodeResult result = executor.execute(config, ctx);
            assertEquals(ResultStatus.SUCCESS, result.status());
        }
    }
}
