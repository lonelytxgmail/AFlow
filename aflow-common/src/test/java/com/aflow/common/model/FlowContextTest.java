package com.aflow.common.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlowContextTest {

    @Test
    void defaultConstructor_initializesDefaults() {
        FlowContext ctx = new FlowContext();
        assertNull(ctx.getFlowInstanceId());
        assertNull(ctx.getFlowDefinitionId());
        assertNotNull(ctx.getVariables());
        assertTrue(ctx.getVariables().isEmpty());
        assertNotNull(ctx.getMetadata());
        assertEquals(FlowStatus.PENDING, ctx.getStatus());
        assertNotNull(ctx.getExecutionPath());
        assertTrue(ctx.getExecutionPath().isEmpty());
        assertNotNull(ctx.getBreakpoints());
        assertFalse(ctx.isDebugMode());
    }

    @Test
    void parameterizedConstructor_setsIds() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        assertEquals("inst-1", ctx.getFlowInstanceId());
        assertEquals("def-1", ctx.getFlowDefinitionId());
    }

    @Test
    void putVariable_and_getVariable() {
        FlowContext ctx = new FlowContext();
        ctx.putVariable("key", "value");
        ctx.putVariable("num", 42);

        assertEquals("value", ctx.getVariable("key"));
        assertEquals(42, (int) ctx.getVariable("num"));
        assertNull(ctx.getVariable("missing"));
    }

    @Test
    void mergeOutputs_mergesIntoVariables() {
        FlowContext ctx = new FlowContext();
        ctx.putVariable("existing", "keep");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("a", 1);
        outputs.put("b", "two");
        ctx.mergeOutputs(outputs);

        assertEquals("keep", ctx.getVariable("existing"));
        assertEquals(1, (int) ctx.getVariable("a"));
        assertEquals("two", ctx.getVariable("b"));
    }

    @Test
    void mergeOutputs_nullIsIgnored() {
        FlowContext ctx = new FlowContext();
        ctx.putVariable("x", "y");
        ctx.mergeOutputs(null);
        assertEquals("y", ctx.getVariable("x"));
        assertEquals(1, ctx.getVariables().size());
    }

    @Test
    void recordNodeExecution_addsToPathAndSetsCurrent() {
        FlowContext ctx = new FlowContext();
        ctx.recordNodeExecution("node-1");
        ctx.recordNodeExecution("node-2");

        assertEquals("node-2", ctx.getCurrentNodeId());
        assertEquals(2, ctx.getExecutionPath().size());
        assertEquals("node-1", ctx.getExecutionPath().get(0));
        assertEquals("node-2", ctx.getExecutionPath().get(1));
    }

    @Test
    void hasBreakpoint_returnsTrueWhenSet() {
        FlowContext ctx = new FlowContext();
        ctx.getBreakpoints().add("node-A");
        assertTrue(ctx.hasBreakpoint("node-A"));
        assertFalse(ctx.hasBreakpoint("node-B"));
    }

    @Test
    void setVariables_nullFallsBackToEmptyMap() {
        FlowContext ctx = new FlowContext();
        ctx.setVariables(null);
        assertNotNull(ctx.getVariables());
        assertTrue(ctx.getVariables().isEmpty());
    }

    @Test
    void setMetadata_nullFallsBackToEmptyMap() {
        FlowContext ctx = new FlowContext();
        ctx.setMetadata(null);
        assertNotNull(ctx.getMetadata());
        assertTrue(ctx.getMetadata().isEmpty());
    }

    @Test
    void setExecutionPath_nullFallsBackToEmptyList() {
        FlowContext ctx = new FlowContext();
        ctx.setExecutionPath(null);
        assertNotNull(ctx.getExecutionPath());
        assertTrue(ctx.getExecutionPath().isEmpty());
    }

    @Test
    void setBreakpoints_nullFallsBackToEmptySet() {
        FlowContext ctx = new FlowContext();
        ctx.setBreakpoints(null);
        assertNotNull(ctx.getBreakpoints());
        assertTrue(ctx.getBreakpoints().isEmpty());
    }

    @Test
    void debugMode_toggle() {
        FlowContext ctx = new FlowContext();
        assertFalse(ctx.isDebugMode());
        ctx.setDebugMode(true);
        assertTrue(ctx.isDebugMode());
    }

    @Test
    void toString_containsInstanceIdAndStatus() {
        FlowContext ctx = new FlowContext("inst-1", "def-1");
        ctx.setStatus(FlowStatus.RUNNING);
        String str = ctx.toString();
        assertTrue(str.contains("inst-1"));
        assertTrue(str.contains("RUNNING"));
    }
}
