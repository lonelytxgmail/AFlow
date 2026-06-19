package com.aflow.core.engine;

import com.aflow.common.exception.FlowExecutionException;
import com.aflow.common.exception.FlowNotFoundException;
import com.aflow.common.model.*;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.core.dsl.DslParser;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventBus;
import com.aflow.core.expression.ExpressionEvaluator;
import com.aflow.core.registry.NodeRegistry;
import com.aflow.core.snapshot.SnapshotManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultWorkflowEngineTest {

    @Mock private NodeRegistry nodeRegistry;
    @Mock private DslParser dslParser;
    @Mock private ExpressionEvaluator expressionEvaluator;
    @Mock private SnapshotManager snapshotManager;
    @Mock private EventPersistenceService eventPersistenceService;
    @Mock private FlowDefinitionPersistenceService definitionPersistence;
    @Mock private FlowInstancePersistenceService instancePersistence;
    @Mock private FlowEventBus flowEventBus;
    @Mock private com.aflow.core.metrics.EngineMetrics engineMetrics;
    @Mock private NodeExecutor logExecutor;

    private DefaultWorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultWorkflowEngine(
                nodeRegistry, dslParser, expressionEvaluator,
                snapshotManager, eventPersistenceService,
                definitionPersistence, instancePersistence,
                flowEventBus, engineMetrics,
                10000L, 30L, false, 300000L
        );
    }

    @Test
    void start_simpleFlow_executesAndCompletes() throws Exception {
        FlowDefinition def = buildTwoNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any()))
                .thenReturn(NodeResult.success(Map.of("result", "ok")));

        FlowContext result = engine.start("def-1", Map.of("input", "value"));

        assertNotNull(result);
        assertEquals(FlowStatus.COMPLETED, result.getStatus());
        assertEquals("def-1", result.getFlowDefinitionId());
        verify(instancePersistence, atLeastOnce()).save(any());
    }

    @Test
    void start_definitionNotFound_throws() {
        when(definitionPersistence.findDslContentById("unknown")).thenReturn(null);
        assertThrows(FlowNotFoundException.class, () -> engine.start("unknown", null));
    }

    @Test
    void start_nodeExecutionFails_flowFails() throws Exception {
        FlowDefinition def = buildTwoNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any()))
                .thenReturn(NodeResult.failed("Execution error"));

        FlowContext result = engine.start("def-1", null);

        assertEquals(FlowStatus.FAILED, result.getStatus());
    }

    @Test
    void start_nodeReturnsSuspended_flowSuspended() throws Exception {
        FlowDefinition def = buildTwoNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.suspended());

        FlowContext result = engine.start("def-1", null);

        assertEquals(FlowStatus.SUSPENDED, result.getStatus());
    }

    @Test
    void start_withInputs_inputsMergedIntoContext() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of()));

        Map<String, Object> inputs = Map.of("key", "val");
        FlowContext result = engine.start("def-1", inputs);

        assertEquals("val", result.getVariable("key"));
    }

    @Test
    void cancel_activeFlow_setsCancelled() {
        // First start a flow
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.suspended());

        FlowContext started = engine.start("def-1", null);
        String instanceId = started.getFlowInstanceId();

        FlowContext cancelled = engine.cancel(instanceId);
        assertEquals(FlowStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void suspend_activeFlow_setsSuspended() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.suspended());

        FlowContext started = engine.start("def-1", null);
        String instanceId = started.getFlowInstanceId();

        FlowContext suspended = engine.suspend(instanceId);
        assertEquals(FlowStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    void resume_nonSuspendedFlow_throws() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of()));

        FlowContext completed = engine.start("def-1", null);
        // Flow completed and was removed from cache
        when(instancePersistence.findById(completed.getFlowInstanceId()))
                .thenReturn(Optional.of(completed));

        assertThrows(FlowExecutionException.class, () ->
                engine.resume(completed.getFlowInstanceId(), null));
    }

    @Test
    void addBreakpoint_and_getBreakpoints() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.suspended());

        FlowContext started = engine.start("def-1", null);
        String instanceId = started.getFlowInstanceId();

        engine.addBreakpoint(instanceId, "n1");
        var breakpoints = engine.getBreakpoints(instanceId);
        assertTrue(breakpoints.contains("n1"));

        engine.removeBreakpoint(instanceId, "n1");
        breakpoints = engine.getBreakpoints(instanceId);
        assertFalse(breakpoints.contains("n1"));
    }

    @Test
    void invalidateDefinitionCache_removesFromCache() {
        // Start a flow to populate cache
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of()));

        engine.start("def-1", null);
        // Should not throw
        engine.invalidateDefinitionCache("def-1");
    }

    @Test
    void updateContext_whenSuspended_updatesVariables() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.suspended());

        FlowContext started = engine.start("def-1", null);
        String instanceId = started.getFlowInstanceId();

        FlowContext updated = engine.updateContext(instanceId, Map.of("newVar", "newValue"));
        assertEquals("newValue", updated.getVariable("newVar"));
    }

    @Test
    void updateContext_whenNotSuspended_throws() {
        FlowDefinition def = buildSingleNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of()));

        FlowContext completed = engine.start("def-1", null);
        when(instancePersistence.findById(completed.getFlowInstanceId()))
                .thenReturn(Optional.of(completed));

        assertThrows(FlowExecutionException.class, () ->
                engine.updateContext(completed.getFlowInstanceId(), Map.of("x", "y")));
    }

    @Test
    void start_withBreakpoint_suspendsBeforeTargetNodeExecution() {
        FlowDefinition def = buildTwoNodeDefinition();
        def.getNodes().get(1).setBreakpoint(true);
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of("done", true)));

        FlowContext context = engine.start("def-1", null);

        assertEquals(FlowStatus.SUSPENDED, context.getStatus());
        assertEquals("n2", context.getCurrentNodeId());
        assertEquals(List.of("n1"), context.getExecutionPath());
        verify(logExecutor, times(1)).execute(any(), any());
        verify(eventPersistenceService).saveEvent(eq(context.getFlowInstanceId()), eq("n2"),
                eq(com.aflow.core.event.FlowEventType.NODE_SUSPENDED), contains("breakpoint"), eq(0L));
    }

    @Test
    void step_executesOneNodeAndSuspendsAtNextNode() {
        FlowDefinition def = buildTwoNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of("done", true)));

        FlowContext context = new FlowContext("inst-1", "def-1");
        context.setStatus(FlowStatus.SUSPENDED);
        when(instancePersistence.findById("inst-1")).thenReturn(Optional.of(context));

        FlowContext stepped = engine.step("inst-1");

        assertEquals(FlowStatus.SUSPENDED, stepped.getStatus());
        assertEquals("n2", stepped.getCurrentNodeId());
        assertEquals(List.of("n1"), stepped.getExecutionPath());
        assertTrue(stepped.isDebugMode());
        verify(logExecutor, times(1)).execute(any(), any());
    }

    @Test
    void resume_fromBreakpoint_doesNotSuspendAgainOnSameNode() {
        FlowDefinition def = buildTwoNodeDefinition();
        when(definitionPersistence.findDslContentById("def-1")).thenReturn("{}");
        when(dslParser.parse("{}")).thenReturn(def);
        when(nodeRegistry.getExecutor("log")).thenReturn(logExecutor);
        when(logExecutor.execute(any(), any())).thenReturn(NodeResult.success(Map.of("done", true)));

        FlowContext seeded = new FlowContext("inst-1", "def-1");
        seeded.setStatus(FlowStatus.SUSPENDED);
        seeded.setCurrentNodeId("n2");
        seeded.setExecutionPath(new java.util.ArrayList<>(List.of("n1")));
        seeded.setBreakpoints(new java.util.HashSet<>(Set.of("n2")));
        when(instancePersistence.findById("inst-1")).thenReturn(Optional.of(seeded));

        FlowContext resumed = engine.resume("inst-1", null);

        assertEquals(FlowStatus.COMPLETED, resumed.getStatus());
        assertNull(resumed.getCurrentNodeId());
        assertEquals(List.of("n1", "n2"), resumed.getExecutionPath());
        verify(logExecutor, times(1)).execute(any(), any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private FlowDefinition buildSingleNodeDefinition() {
        FlowDefinition def = new FlowDefinition();
        def.setId("def-1");
        def.setName("Test");
        def.setNodes(List.of(new NodeDefinition("n1", "log")));
        def.setEdges(List.of());
        return def;
    }

    private FlowDefinition buildTwoNodeDefinition() {
        FlowDefinition def = new FlowDefinition();
        def.setId("def-1");
        def.setName("Test");
        def.setNodes(List.of(
                new NodeDefinition("n1", "log"),
                new NodeDefinition("n2", "log")
        ));
        def.setEdges(List.of(new EdgeDefinition("n1", "n2")));
        return def;
    }
}
