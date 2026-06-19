package com.aflow.core.registry;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.exception.UnknownNodeTypeException;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class NodeRegistryTest {

    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
    }

    @Test
    void setApplicationContext_discoversAnnotatedBeans() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("testLog", TestLogExecutor.class);
        ctx.refresh();

        registry.setApplicationContext(ctx);

        assertTrue(registry.hasExecutor("test-log"));
        assertEquals(1, registry.size());
        ctx.close();
    }

    @Test
    void getExecutor_returnsCorrectExecutor() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("testLog", TestLogExecutor.class);
        ctx.refresh();

        registry.setApplicationContext(ctx);

        NodeExecutor executor = registry.getExecutor("test-log");
        assertNotNull(executor);
        assertInstanceOf(TestLogExecutor.class, executor);
        ctx.close();
    }

    @Test
    void getExecutor_unknownType_throws() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();
        registry.setApplicationContext(ctx);

        assertThrows(UnknownNodeTypeException.class, () ->
                registry.getExecutor("nonexistent"));
        ctx.close();
    }

    @Test
    void hasExecutor_returnsFalseForUnregistered() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();
        registry.setApplicationContext(ctx);

        assertFalse(registry.hasExecutor("unknown-type"));
        ctx.close();
    }

    @Test
    void getAllMetadata_returnsImmutableMap() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("testLog", TestLogExecutor.class);
        ctx.refresh();

        registry.setApplicationContext(ctx);

        var metadata = registry.getAllMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("test-log"));
        assertThrows(UnsupportedOperationException.class, () ->
                metadata.put("hack", null));
        ctx.close();
    }

    @Test
    void multipleExecutors_allRegistered() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean("testLog", TestLogExecutor.class);
        ctx.registerBean("testScript", TestScriptExecutor.class);
        ctx.refresh();

        registry.setApplicationContext(ctx);

        assertEquals(2, registry.size());
        assertTrue(registry.hasExecutor("test-log"));
        assertTrue(registry.hasExecutor("test-script"));
        ctx.close();
    }

    // ─── Test Node Executors ─────────────────────────────────────────

    @FlowNode(type = "test-log", name = "Test Log", description = "Test log node")
    static class TestLogExecutor implements NodeExecutor {
        @Override
        public NodeResult execute(NodeConfig config, FlowContext context) {
            return NodeResult.success(null);
        }
    }

    @FlowNode(type = "test-script", name = "Test Script", description = "Test script node")
    static class TestScriptExecutor implements NodeExecutor {
        @Override
        public NodeResult execute(NodeConfig config, FlowContext context) {
            return NodeResult.success(null);
        }
    }
}
