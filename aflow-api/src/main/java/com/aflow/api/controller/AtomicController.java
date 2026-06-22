package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.dto.AtomicCallRequest;
import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import com.aflow.core.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/atomic")
public class AtomicController {

    private static final Logger log = LoggerFactory.getLogger(AtomicController.class);

    private final NodeRegistry registry;

    public AtomicController(NodeRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/{componentType}")
    public ApiResponse<NodeResult> invoke(@PathVariable String componentType, @RequestBody AtomicCallRequest request) {
        log.info("Atomic invoke: type={}", componentType);
        NodeExecutor executor = registry.getExecutor(componentType);
        FlowContext tempContext = new FlowContext(UUID.randomUUID().toString(), "atomic");
        if (request.inputs() != null) {
            tempContext.mergeOutputs(request.inputs());
        }
        NodeConfig config = new NodeConfig();
        config.setType(componentType);
        config.setConfig(request.config() != null ? request.config() : Map.of());
        NodeResult result = executor.execute(config, tempContext);
        log.info("Atomic invoke completed: type={}, status={}", componentType, result.status());
        return ApiResponse.ok(result);
    }

    @GetMapping("/node-registry")
    public ApiResponse<List<Map<String, Object>>> registry() {
        List<Map<String, Object>> components = new ArrayList<>();
        registry.getAllMetadata().forEach((type, annotation) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", type);
            info.put("name", annotation.name());
            info.put("description", annotation.description());
            String schema = annotation.configSchema();
            info.put("configSchema", (schema != null && !schema.isBlank()) ? schema : "{}");
            components.add(info);
        });
        return ApiResponse.ok(components);
    }
}
