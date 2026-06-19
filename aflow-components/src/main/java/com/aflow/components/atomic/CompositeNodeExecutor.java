package com.aflow.components.atomic;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.common.util.JsonUtil;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import com.aflow.core.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 组合原子能力节点执行器。
 * <p>
 * 当流程 DSL 中引用一个原子能力（通过 {@code componentId}）时，
 * 此执行器负责：
 * <ol>
 *   <li>根据 componentId 加载原子能力配置模板</li>
 *   <li>将用户传入的参数合并到模板中</li>
 *   <li>委托给实际的 NodeExecutor 执行</li>
 * </ol>
 * <p>
 * <b>配置参数（NodeConfig.config）：</b>
 * <ul>
 *   <li><code>componentId</code> — 引用的原子能力 ID（必填）</li>
 *   <li><code>params</code> — 调用参数（覆盖模板默认值）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Component
@FlowNode(type = "composite", name = "组合原子能力", description = "引用已注册的原子能力模板，传入参数即可执行")
public class CompositeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompositeNodeExecutor.class);

    private final AtomicComponentPersistenceService atomicPersistence;
    private final ObjectProvider<NodeRegistry> nodeRegistryProvider;

    public CompositeNodeExecutor(AtomicComponentPersistenceService atomicPersistence,
                                 ObjectProvider<NodeRegistry> nodeRegistryProvider) {
        this.atomicPersistence = atomicPersistence;
        this.nodeRegistryProvider = nodeRegistryProvider;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();
        if (cfg == null || !cfg.containsKey("componentId")) {
            throw new IllegalArgumentException("组合节点缺少 componentId 配置");
        }

        String componentId = String.valueOf(cfg.get("componentId"));
        log.info("组合节点执行: componentId={}", componentId);

        // 1. 加载原子能力
        AtomicComponent component = atomicPersistence.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("原子能力不存在: " + componentId));
        if (!"PUBLISHED".equalsIgnoreCase(component.getStatus())) {
            throw new IllegalArgumentException("原子能力未发布，无法执行: " + componentId);
        }

        // 2. 解析配置模板，合并用户参数
        Map<String, Object> templateConfig = parseConfigTemplate(component.getConfigTemplate());
        @SuppressWarnings("unchecked")
        Map<String, Object> userParams = cfg.get("params") instanceof Map ? (Map<String, Object>) cfg.get("params") : Map.of();
        validateRequiredInputs(component, userParams);
        templateConfig.putAll(userParams);

        // 3. 创建实际执行器的 NodeConfig
        NodeConfig delegateConfig = new NodeConfig(component.getNodeType(), templateConfig, config.getOutputVariable());

        // 4. 委托给实际的 NodeExecutor 执行
        NodeExecutor delegateExecutor = nodeRegistryProvider.getObject().getExecutor(component.getNodeType());
        if (delegateExecutor == null) {
            throw new IllegalArgumentException("节点执行器不存在: type=" + component.getNodeType());
        }

        log.info("组合节点委托执行: componentId={}, nodeType={}", componentId, component.getNodeType());
        NodeResult result = delegateExecutor.execute(delegateConfig, context);

        log.info("组合节点执行完成: componentId={}, status={}", componentId, result.status());
        return result;
    }

    /**
     * 解析配置模板 JSON 为 Map。
     */
    private Map<String, Object> parseConfigTemplate(String configTemplate) {
        if (configTemplate == null || configTemplate.isEmpty()) {
            return new java.util.HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JsonUtil.fromJson(configTemplate, Map.class);
            return map != null ? new java.util.HashMap<>(map) : new java.util.HashMap<>();
        } catch (Exception e) {
            log.warn("解析配置模板失败: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private void validateRequiredInputs(AtomicComponent component, Map<String, Object> params) {
        String inputSchema = component.getInputSchema();
        if (inputSchema == null || inputSchema.isBlank()) {
            return;
        }
        try {
            Map<String, Object> schema = JsonUtil.fromJson(inputSchema, Map.class);
            Object required = schema != null ? schema.get("required") : null;
            if (required instanceof Iterable<?> requiredFields) {
                for (Object field : requiredFields) {
                    String key = String.valueOf(field);
                    if (!params.containsKey(key) || params.get(key) == null) {
                        throw new IllegalArgumentException("原子能力缺少必填输入: " + key);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("原子能力输入 Schema 无法解析: " + component.getId(), e);
        }
    }
}
