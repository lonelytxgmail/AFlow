package com.aflow.components.condition;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import com.aflow.core.expression.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionNodeExecutorTest {

    private ConditionNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConditionNodeExecutor(new ExpressionEvaluator());
    }

    @Test
    void execute_trueCondition_routesToTrueNext() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("status", 200);

        NodeConfig config = new NodeConfig("condition", Map.of(
                "expression", "#status == 200",
                "trueNext", "node-yes",
                "falseNext", "node-no"
        ), null);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("node-yes", result.nextNodeId());
        assertEquals(true, result.outputs().get("conditionResult"));
    }

    @Test
    void execute_falseCondition_routesToFalseNext() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("status", 404);

        NodeConfig config = new NodeConfig("condition", Map.of(
                "expression", "#status == 200",
                "trueNext", "node-yes",
                "falseNext", "node-no"
        ), null);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("node-no", result.nextNodeId());
        assertEquals(false, result.outputs().get("conditionResult"));
    }

    @Test
    void execute_invalidExpression_returnsFailed() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeConfig config = new NodeConfig("condition", Map.of(
                "expression", "###invalid!!!",
                "trueNext", "node-yes",
                "falseNext", "node-no"
        ), null);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.FAILED, result.status());
        assertNotNull(result.errorMessage());
    }

    @Test
    void execute_noExpression_defaultsFalse() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeConfig config = new NodeConfig("condition", Map.of(
                "trueNext", "yes",
                "falseNext", "no"
        ), null);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("no", result.nextNodeId());
    }

    @Test
    void execute_noNextNodes_nullNext() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("x", true);

        NodeConfig config = new NodeConfig("condition", Map.of(
                "expression", "#x"
        ), null);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertNull(result.nextNodeId());
    }
}
