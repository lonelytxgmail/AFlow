package com.aflow.components.assign;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import com.aflow.core.expression.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssignNodeExecutorTest {

    private AssignNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AssignNodeExecutor(new ExpressionEvaluator());
    }

    @Test
    void execute_literalAssignments() {
        Map<String, Object> assignments = Map.of("x", 42, "y", "hello");
        NodeConfig config = new NodeConfig("assign", Map.of("assignments", assignments), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals(42, result.outputs().get("x"));
        assertEquals("hello", result.outputs().get("y"));
    }

    @Test
    void execute_spelExpressionAssignment() {
        Map<String, Object> assignments = Map.of("doubled", "#value * 2");
        NodeConfig config = new NodeConfig("assign", Map.of("assignments", assignments), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("value", 10);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        // The SpEL expression #value * 2 starts with #, so it's evaluated
        assertEquals(20, result.outputs().get("doubled"));
    }

    @Test
    void execute_missingAssignments_returnsFailed() {
        NodeConfig config = new NodeConfig("assign", Map.of(), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.FAILED, result.status());
    }

    @Test
    void execute_invalidSpel_keepsOriginalString() {
        // Value starts with # but is not valid SpEL syntax
        Map<String, Object> assignments = new HashMap<>();
        assignments.put("bad", "###invalid!!!");
        NodeConfig config = new NodeConfig("assign", Map.of("assignments", assignments), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        // When SpEL evaluation fails (syntax error), the original string is kept
        assertEquals("###invalid!!!", result.outputs().get("bad"));
    }

    @Test
    void execute_nonStringValue_passedDirectly() {
        Map<String, Object> assignments = Map.of("num", 100, "bool", true);
        NodeConfig config = new NodeConfig("assign", Map.of("assignments", assignments), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals(100, result.outputs().get("num"));
        assertEquals(true, result.outputs().get("bool"));
    }
}
