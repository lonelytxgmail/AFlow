package com.aflow.components.atomic;

import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import com.aflow.core.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeNodeExecutorTest {

    @Mock private AtomicComponentPersistenceService atomicPersistence;
    @Mock private ObjectProvider<NodeRegistry> nodeRegistryProvider;
    @Mock private NodeRegistry nodeRegistry;
    @Mock private NodeExecutor delegateExecutor;

    private CompositeNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CompositeNodeExecutor(atomicPersistence, nodeRegistryProvider);
    }

    @Test
    void execute_publishedComponent_mergesTemplateAndParams() {
        AtomicComponent component = buildComponent("component-1", "PUBLISHED");
        when(atomicPersistence.findById("component-1")).thenReturn(Optional.of(component));
        when(nodeRegistryProvider.getObject()).thenReturn(nodeRegistry);
        when(nodeRegistry.getExecutor("assign")).thenReturn(delegateExecutor);
        when(delegateExecutor.execute(any(NodeConfig.class), any(FlowContext.class)))
                .thenReturn(NodeResult.success(Map.of("message", "ok")));

        FlowContext context = new FlowContext("inst-1", "def-1");
        NodeConfig config = new NodeConfig("composite", Map.of(
                "componentId", "component-1",
                "params", Map.of("value", "runtime-value")
        ), "result");

        NodeResult result = executor.execute(config, context);

        assertEquals("SUCCESS", result.status().name());
        verify(nodeRegistry).getExecutor("assign");
        verify(delegateExecutor).execute(any(NodeConfig.class), eq(context));
    }

    @Test
    void execute_missingRequiredInput_throws() {
        AtomicComponent component = buildComponent("component-1", "PUBLISHED");
        when(atomicPersistence.findById("component-1")).thenReturn(Optional.of(component));

        NodeConfig config = new NodeConfig("composite", Map.of(
                "componentId", "component-1",
                "params", Map.of()
        ), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(config, new FlowContext("inst-1", "def-1")));
        assertEquals("原子能力缺少必填输入: value", ex.getMessage());
    }

    @Test
    void execute_unpublishedComponent_rejected() {
        AtomicComponent component = buildComponent("component-1", "DRAFT");
        when(atomicPersistence.findById("component-1")).thenReturn(Optional.of(component));

        NodeConfig config = new NodeConfig("composite", Map.of(
                "componentId", "component-1",
                "params", Map.of("value", "x")
        ), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(config, new FlowContext("inst-1", "def-1")));
        assertEquals("原子能力未发布，无法执行: component-1", ex.getMessage());
    }

    @Test
    void execute_missingComponentId_throws() {
        NodeConfig config = new NodeConfig("composite", Map.of(), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(config, new FlowContext("inst-1", "def-1")));
        assertEquals("组合节点缺少 componentId 配置", ex.getMessage());
    }

    private AtomicComponent buildComponent(String id, String status) {
        AtomicComponent component = new AtomicComponent();
        component.setId(id);
        component.setName("Assign Tool");
        component.setNodeType("assign");
        component.setStatus(status);
        component.setConfigTemplate("{\"expression\":\"template-value\"}");
        component.setInputSchema("{\"required\":[\"value\"]}");
        return component;
    }
}
