package com.aflow.agent.tool;

import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Tool 注册表。
 * <p>
 * Tool 优先来源于已发布的原子能力，其次才是显式标注 {@link Tool} 的内置执行器。
 * 这样 Agent 默认只能调用已发布能力，避免直接暴露任意 NodeExecutor。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final String EMPTY_TOOL_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final Map<String, NodeExecutorToolAdapter> tools = new ConcurrentHashMap<>();
    private final List<NodeExecutor> nodeExecutors;
    private final AtomicComponentPersistenceService atomicComponentPersistenceService;

    public ToolRegistry(List<NodeExecutor> nodeExecutors,
                        AtomicComponentPersistenceService atomicComponentPersistenceService) {
        this.nodeExecutors = nodeExecutors;
        this.atomicComponentPersistenceService = atomicComponentPersistenceService;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        tools.clear();
        registerAtomicComponentTools();
        registerAnnotatedNodeTools();
        log.info("Agent Tool 注册完成: 总数={}, tools={}", tools.size(), tools.keySet());
    }

    public NodeExecutorToolAdapter getTool(String name) {
        return tools.get(name);
    }

    public List<NodeExecutorToolAdapter> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public List<NodeExecutorToolAdapter> getToolsByNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return getAllTools();
        }

        List<NodeExecutorToolAdapter> selected = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            NodeExecutorToolAdapter tool = tools.get(name);
            if (tool != null) {
                selected.add(tool);
            }
        }
        return selected;
    }

    public String buildToolDescriptions() {
        return buildToolDescriptions(getAllTools());
    }

    public String buildToolDescriptions(Collection<NodeExecutorToolAdapter> selectedTools) {
        if (selectedTools == null || selectedTools.isEmpty()) {
            return "No tools available.";
        }

        StringBuilder sb = new StringBuilder("Available tools:\n");
        for (NodeExecutorToolAdapter tool : selectedTools) {
            sb.append("- ").append(tool.getName())
                    .append(": ").append(tool.getDescription())
                    .append(". Parameters: ").append(tool.getParameterSchema())
                    .append("\n");
        }
        return sb.toString();
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    private void registerAtomicComponentTools() {
        List<AtomicComponent> publishedComponents = atomicComponentPersistenceService.findByStatus("PUBLISHED");
        for (AtomicComponent component : publishedComponents) {
            try {
                NodeExecutorToolAdapter adapter = new NodeExecutorToolAdapter(
                        new AtomicComponentToolExecutor(component),
                        buildAtomicToolName(component),
                        component.getDescription() != null && !component.getDescription().isBlank()
                                ? component.getDescription()
                                : "Published atomic component: " + component.getName(),
                        component.getInputSchema() != null && !component.getInputSchema().isBlank()
                                ? component.getInputSchema()
                                : EMPTY_TOOL_SCHEMA
                );
                tools.put(adapter.getName(), adapter);
                log.info("注册原子能力 Agent Tool: name={}, componentId={}", adapter.getName(), component.getId());
            } catch (Exception e) {
                log.warn("注册原子能力 Tool 失败: componentId={}, error={}", component.getId(), e.getMessage());
            }
        }
    }

    private void registerAnnotatedNodeTools() {
        for (NodeExecutor executor : nodeExecutors) {
            if (!executor.getClass().isAnnotationPresent(Tool.class)) {
                continue;
            }
            if ("AgentNodeExecutor".equals(executor.getClass().getSimpleName())) {
                log.debug("跳过自引用 Agent Tool: executor={}", executor.getClass().getSimpleName());
                continue;
            }
            try {
                NodeExecutorToolAdapter adapter = new NodeExecutorToolAdapter(executor);
                tools.put(adapter.getName(), adapter);
                log.info("注册内置 Agent Tool: name={}, description={}", adapter.getName(), adapter.getDescription());
            } catch (Exception e) {
                log.warn("注册 Tool 失败: executor={}, error={}",
                        executor.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private String buildAtomicToolName(AtomicComponent component) {
        return "atomic_" + component.getId().replace('-', '_');
    }

    private NodeExecutor findCompositeExecutor() {
        return nodeExecutors.stream()
                .filter(executor -> executor.getClass().getSimpleName().equals("CompositeNodeExecutor"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("CompositeNodeExecutor not available"));
    }

    private final class AtomicComponentToolExecutor implements NodeExecutor {

        private final AtomicComponent component;

        private AtomicComponentToolExecutor(AtomicComponent component) {
            this.component = component;
        }

        @Override
        public NodeResult execute(NodeConfig config, FlowContext context) {
            Map<String, Object> compositeConfig = new HashMap<>();
            compositeConfig.put("componentId", component.getId());
            compositeConfig.put("params", config.getConfig());
            return findCompositeExecutor().execute(new NodeConfig("composite", compositeConfig, null), context);
        }
    }
}
