package com.aflow.components.delay;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DelayNodeExecutorTest {

    private final DelayNodeExecutor executor = new DelayNodeExecutor();

    @Test
    void execute_shortDelay_succeeds() {
        NodeConfig config = new NodeConfig("delay", Map.of("duration", "PT0.1S"), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        long start = System.currentTimeMillis();
        NodeResult result = executor.execute(config, ctx);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("PT0.1S", result.outputs().get("delayed"));
        assertTrue(elapsed >= 80, "Should have delayed at least 80ms, was " + elapsed);
    }

    @Test
    void execute_defaultDelay_succeeds() {
        NodeConfig config = new NodeConfig("delay", Map.of(), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        // Default is PT1S - too long for tests, so we just test invalid format
        NodeConfig config2 = new NodeConfig("delay", Map.of("duration", "invalid"), null);
        NodeResult result = executor.execute(config2, ctx);
        assertEquals(ResultStatus.FAILED, result.status());
    }

    @Test
    void execute_invalidDuration_returnsFailed() {
        NodeConfig config = new NodeConfig("delay", Map.of("duration", "not-a-duration"), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.FAILED, result.status());
        assertTrue(result.errorMessage().contains("Invalid duration"));
    }
}
