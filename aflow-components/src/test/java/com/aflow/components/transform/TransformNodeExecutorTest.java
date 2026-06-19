package com.aflow.components.transform;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.ResultStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransformNodeExecutorTest {

    private final TransformNodeExecutor executor = new TransformNodeExecutor();

    @Test
    void execute_simpleMapping() {
        Map<String, Object> mapping = Map.of("greeting", "'Hello ' + #name");
        NodeConfig config = new NodeConfig("transform", Map.of("mapping", mapping), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("name", "World");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals("Hello World", result.outputs().get("greeting"));
    }

    @Test
    void execute_mathMapping() {
        Map<String, Object> mapping = Map.of("total", "#price * #quantity");
        NodeConfig config = new NodeConfig("transform", Map.of("mapping", mapping), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("price", 10);
        ctx.putVariable("quantity", 5);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals(50, result.outputs().get("total"));
    }

    @Test
    void execute_missingMapping_returnsFailed() {
        NodeConfig config = new NodeConfig("transform", Map.of(), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.FAILED, result.status());
    }

    @Test
    void execute_invalidExpression_outputsNull() {
        Map<String, Object> mapping = Map.of("bad", "###invalid!!!");
        NodeConfig config = new NodeConfig("transform", Map.of("mapping", mapping), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertNull(result.outputs().get("bad"));
    }

    @Test
    void execute_multipleMappings() {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("sum", "#a + #b");
        mapping.put("product", "#a * #b");
        NodeConfig config = new NodeConfig("transform", Map.of("mapping", mapping), null);
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.putVariable("a", 3);
        ctx.putVariable("b", 4);

        NodeResult result = executor.execute(config, ctx);
        assertEquals(ResultStatus.SUCCESS, result.status());
        assertEquals(7, result.outputs().get("sum"));
        assertEquals(12, result.outputs().get("product"));
    }
}
