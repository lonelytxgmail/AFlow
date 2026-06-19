package com.aflow.agent.tool;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryTest {

    @Test
    void reload_registersPublishedAtomicComponentsAndAnnotatedExecutors() {
        AtomicComponentPersistenceService atomicService = mock(AtomicComponentPersistenceService.class);
        AtomicComponent published = new AtomicComponent();
        published.setId("component-1");
        published.setName("Fetch User");
        published.setDescription("Fetch user profile");
        published.setInputSchema("{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\"}}}");
        published.setStatus("PUBLISHED");
        when(atomicService.findByStatus("PUBLISHED")).thenReturn(List.of(published));

        ToolRegistry registry = new ToolRegistry(List.of(new CompositeNodeExecutorStub(), new HttpToolExecutorStub()), atomicService);
        registry.reload();

        assertNotNull(registry.getTool("atomic_component_1"));
        assertNotNull(registry.getTool("http_tool"));
        assertFalse(registry.getToolNames().contains("agent"));
    }

    @Test
    void getToolsByNames_filtersSelection() {
        AtomicComponentPersistenceService atomicService = mock(AtomicComponentPersistenceService.class);
        when(atomicService.findByStatus("PUBLISHED")).thenReturn(List.of());

        ToolRegistry registry = new ToolRegistry(List.of(new CompositeNodeExecutorStub(), new HttpToolExecutorStub()), atomicService);
        registry.reload();

        List<NodeExecutorToolAdapter> selected = registry.getToolsByNames(List.of("http_tool", "missing"));
        assertEquals(1, selected.size());
        assertEquals("http_tool", selected.getFirst().getName());
    }

    @FlowNode(type = "composite", name = "Composite", description = "Composite executor")
    static class CompositeNodeExecutorStub implements NodeExecutor {
        @Override
        public NodeResult execute(NodeConfig config, FlowContext context) {
            return NodeResult.success(Map.of("componentId", config.getConfig().get("componentId")));
        }
    }

    @Tool(
            name = "http_tool",
            description = "HTTP tool",
            parameters = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}"
    )
    @FlowNode(type = "http", name = "HTTP", description = "HTTP executor")
    static class HttpToolExecutorStub implements NodeExecutor {
        @Override
        public NodeResult execute(NodeConfig config, FlowContext context) {
            return NodeResult.success(config.getConfig());
        }
    }
}
