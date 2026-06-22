package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.AtomicComponent;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import com.aflow.core.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 原子能力管理 REST API。
 * <p>
 * 提供原子能力组件的完整 CRUD 操作，前端可通过此 API 管理可复用的原子能力模板。
 *
 * <b>接口列表：</b>
 * <ul>
 *   <li>GET    /api/v1/atomic/components       — 查询所有（支持 ?category=xx &status=xx &keyword=xx）</li>
 *   <li>GET    /api/v1/atomic/components/{id}  — 根据 ID 查询</li>
 *   <li>POST   /api/v1/atomic/components       — 创建原子能力</li>
 *   <li>PUT    /api/v1/atomic/components/{id}  — 更新原子能力</li>
 *   <li>DELETE /api/v1/atomic/components/{id}  — 删除原子能力</li>
 *   <li>GET    /api/v1/atomic/component-registry — 获取所有已发布的原子能力（供流程编辑器使用）</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/atomic")
public class AtomicComponentController {

    private static final Logger log = LoggerFactory.getLogger(AtomicComponentController.class);

    private final AtomicComponentPersistenceService persistenceService;
    private final NodeRegistry nodeRegistry;

    public AtomicComponentController(AtomicComponentPersistenceService persistenceService,
                                     NodeRegistry nodeRegistry) {
        this.persistenceService = persistenceService;
        this.nodeRegistry = nodeRegistry;
    }

    /**
     * 查询原子能力列表。
     * 支持按分类、状态、关键词筛选。
     */
    @GetMapping("/components")
    public ApiResponse<List<AtomicComponent>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        log.debug("查询原子能力列表: category={}, status={}, keyword={}", category, status, keyword);

        List<AtomicComponent> components;
        if (keyword != null && !keyword.isEmpty()) {
            components = persistenceService.searchByName(keyword);
        } else if (category != null && !category.isEmpty()) {
            components = persistenceService.findByCategory(category);
        } else if (status != null && !status.isEmpty()) {
            components = persistenceService.findByStatus(status);
        } else {
            components = persistenceService.findAll();
        }

        return ApiResponse.ok(components);
    }

    /**
     * 根据 ID 查询原子能力。
     */
    @GetMapping("/components/{id}")
    public ApiResponse<AtomicComponent> getById(@PathVariable String id) {
        return persistenceService.findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("ATOMIC_COMPONENT_NOT_FOUND", "原子能力不存在: " + id));
    }

    /**
     * 创建原子能力。
     */
    @PostMapping("/components")
    public ApiResponse<AtomicComponent> create(@RequestBody AtomicComponent component) {
        log.info("创建原子能力: name={}, nodeType={}", component.getName(), component.getNodeType());
        AtomicComponent saved = persistenceService.save(component);
        return ApiResponse.ok(saved);
    }

    /**
     * 更新原子能力。
     */
    @PutMapping("/components/{id}")
    public ApiResponse<AtomicComponent> update(@PathVariable String id, @RequestBody AtomicComponent component) {
        if (persistenceService.findById(id).isEmpty()) {
            return ApiResponse.error("ATOMIC_COMPONENT_NOT_FOUND", "原子能力不存在: " + id);
        }
        component.setId(id);
        AtomicComponent saved = persistenceService.save(component);
        log.info("更新原子能力: id={}", id);
        return ApiResponse.ok(saved);
    }

    /**
     * 删除原子能力。
     */
    @DeleteMapping("/components/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        if (persistenceService.findById(id).isEmpty()) {
            return ApiResponse.error("ATOMIC_COMPONENT_NOT_FOUND", "原子能力不存在: " + id);
        }
        persistenceService.deleteById(id);
        log.info("删除原子能力: id={}", id);
        return ApiResponse.ok(null);
    }

    /**
     * 获取原子能力所需的变量列表。
     * <p>
     * 扫描 configTemplate 中的 ${#xxx} 占位符，返回需要传入的变量清单。
     */
    @GetMapping("/components/{id}/variables")
    public ApiResponse<List<Map<String, String>>> getVariables(@PathVariable String id) {
        AtomicComponent component = persistenceService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("原子能力不存在: " + id));
        List<Map<String, String>> variables = extractVariables(component.getConfigTemplate());
        return ApiResponse.ok(variables);
    }

    /**
     * 调试执行一个原子能力。
     * <p>
     * 请求体直接传入业务参数（变量 map），不需要传 config。
     * 响应包含执行结果和调试信息（解析后的实际配置、耗时、模板解析详情）。
     */
    @PostMapping("/components/{id}/invoke")
    public ApiResponse<Map<String, Object>> invoke(@PathVariable String id, @RequestBody Map<String, Object> variables) {
        AtomicComponent component = persistenceService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("原子能力不存在: " + id));
        if (!"PUBLISHED".equalsIgnoreCase(component.getStatus())) {
            throw new IllegalArgumentException("原子能力未发布，无法调用: " + id);
        }

        long startTime = System.currentTimeMillis();

        // 1. 创建临时 FlowContext，注入用户传入的变量
        FlowContext tempContext = new FlowContext(UUID.randomUUID().toString(), "atomic-" + id);
        if (variables != null && !variables.isEmpty()) {
            tempContext.mergeOutputs(variables);
        }

        // 2. 通过 CompositeNodeExecutor 执行（params 为空，变量全部通过 context 传递）
        NodeExecutor executor = nodeRegistry.getExecutor("composite");
        NodeConfig nodeConfig = new NodeConfig("composite",
                Map.of("componentId", id, "params", Map.of()),
                null);

        NodeResult result = executor.execute(nodeConfig, tempContext);
        long duration = System.currentTimeMillis() - startTime;

        // 3. 构造调试信息
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status().name());
        response.put("output", result.outputs());
        if (result.errorMessage() != null) {
            response.put("error", result.errorMessage());
        }

        // 调试详情
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("duration", duration);
        debug.put("componentName", component.getName());
        debug.put("nodeType", component.getNodeType());
        debug.put("inputVariables", variables != null ? variables : Map.of());

        // 解析后的配置（展示实际发出的请求）
        Map<String, Object> resolvedConfig = buildResolvedConfig(component, tempContext);
        debug.put("resolvedConfig", resolvedConfig);

        response.put("debug", debug);
        return ApiResponse.ok(response);
    }

    /**
     * 从 configTemplate 中提取 ${#xxx} 占位符变量。
     */
    private List<Map<String, String>> extractVariables(String configTemplate) {
        List<Map<String, String>> variables = new java.util.ArrayList<>();
        if (configTemplate == null || configTemplate.isBlank()) return variables;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{#([^}]+)}");
        java.util.regex.Matcher matcher = pattern.matcher(configTemplate);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        while (matcher.find()) {
            String varExpr = matcher.group(1).trim();
            // 提取变量名（忽略属性访问如 env.xxx）
            String varName = varExpr.contains(".") ? varExpr.split("\\.")[0] : varExpr;
            if (seen.add(varName)) {
                Map<String, String> varInfo = new LinkedHashMap<>();
                varInfo.put("name", varName);
                varInfo.put("expression", varExpr);
                // 找到包含这个变量的配置上下文
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(configTemplate.length(), matcher.end() + 20);
                varInfo.put("context", configTemplate.substring(start, end).replaceAll("\\s+", " ").trim());
                variables.add(varInfo);
            }
        }
        return variables;
    }

    /**
     * 构建解析后的配置（用于调试展示）。
     */
    private Map<String, Object> buildResolvedConfig(AtomicComponent component, FlowContext context) {
        String configTemplate = component.getConfigTemplate();
        if (configTemplate == null || configTemplate.isBlank()) return Map.of();
        try {
            String jsonStr = configTemplate.trim();
            // 处理双重序列化
            if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                jsonStr = com.aflow.common.util.JsonUtil.fromJson(jsonStr, String.class);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> template = com.aflow.common.util.JsonUtil.fromJson(jsonStr, Map.class);
            if (template == null) return Map.of();

            Map<String, Object> resolved = new LinkedHashMap<>();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{#?([^}]+)}");
            for (Map.Entry<String, Object> entry : template.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String strVal) {
                    java.util.regex.Matcher m = pattern.matcher(strVal);
                    StringBuilder sb = new StringBuilder();
                    while (m.find()) {
                        String expr = m.group(1).trim();
                        Object resolvedVal = context.getVariables().get(expr);
                        m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                                resolvedVal != null ? resolvedVal.toString() : "${#" + expr + "}"));
                    }
                    m.appendTail(sb);
                    resolved.put(entry.getKey(), sb.toString());
                } else {
                    resolved.put(entry.getKey(), value);
                }
            }
            return resolved;
        } catch (Exception e) {
            return Map.of("error", "Failed to resolve: " + e.getMessage());
        }
    }

    /**
     * 获取所有已发布的原子能力（供流程编辑器 Palette 使用）。
     */
    @GetMapping("/component-registry")
    public ApiResponse<List<Map<String, Object>>> registry() {
        List<AtomicComponent> published = persistenceService.findByStatus("PUBLISHED");
        List<Map<String, Object>> registry = published.stream()
                .map(component -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", component.getId());
                    item.put("name", component.getName());
                    item.put("category", component.getCategory());
                    item.put("nodeType", component.getNodeType());
                    item.put("status", component.getStatus());
                    item.put("inputSchema", component.getInputSchema());
                    item.put("outputSchema", component.getOutputSchema());
                    item.put("description", component.getDescription());
                    return item;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(registry);
    }
}
