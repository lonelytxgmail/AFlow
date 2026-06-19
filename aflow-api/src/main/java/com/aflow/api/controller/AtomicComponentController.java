package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.dto.AtomicCallRequest;
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
     * 独立测试调用一个已发布的原子能力。
     */
    @PostMapping("/components/{id}/invoke")
    public ApiResponse<NodeResult> invoke(@PathVariable String id, @RequestBody AtomicCallRequest request) {
        AtomicComponent component = persistenceService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("原子能力不存在: " + id));
        if (!"PUBLISHED".equalsIgnoreCase(component.getStatus())) {
            throw new IllegalArgumentException("原子能力未发布，无法调用: " + id);
        }

        FlowContext tempContext = new FlowContext(UUID.randomUUID().toString(), "atomic-" + id);
        if (request.inputs() != null) {
            tempContext.mergeOutputs(request.inputs());
        }

        Map<String, Object> config = request.config() != null ? request.config() : Map.of();
        NodeExecutor executor = nodeRegistry.getExecutor("composite");
        NodeConfig nodeConfig = new NodeConfig("composite",
                Map.of("componentId", id, "params", config),
                null);
        return ApiResponse.ok(executor.execute(nodeConfig, tempContext));
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
