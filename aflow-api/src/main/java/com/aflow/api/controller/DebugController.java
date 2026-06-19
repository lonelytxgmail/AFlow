package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.common.model.FlowContext;
import com.aflow.core.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final WorkflowEngine engine;

    public DebugController(WorkflowEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/{flowId}/breakpoint/{nodeId}")
    public ApiResponse<?> addBreakpoint(@PathVariable String flowId, @PathVariable String nodeId) {
        log.info("Adding breakpoint: flowId={}, nodeId={}", flowId, nodeId);
        engine.addBreakpoint(flowId, nodeId);
        return ApiResponse.ok("Breakpoint added");
    }

    @DeleteMapping("/{flowId}/breakpoint/{nodeId}")
    public ApiResponse<?> removeBreakpoint(@PathVariable String flowId, @PathVariable String nodeId) {
        log.info("Removing breakpoint: flowId={}, nodeId={}", flowId, nodeId);
        engine.removeBreakpoint(flowId, nodeId);
        return ApiResponse.ok("Breakpoint removed");
    }

    @GetMapping("/{flowId}/breakpoints")
    public ApiResponse<Set<String>> getBreakpoints(@PathVariable String flowId) {
        return ApiResponse.ok(engine.getBreakpoints(flowId));
    }

    @PostMapping("/{flowId}/step")
    public ApiResponse<FlowContext> step(@PathVariable String flowId) {
        log.info("Debug step: flowId={}", flowId);
        return ApiResponse.ok(engine.step(flowId));
    }

    @PutMapping("/{flowId}/context")
    public ApiResponse<FlowContext> updateContext(@PathVariable String flowId, @RequestBody Map<String, Object> variables) {
        log.info("Updating context: flowId={}, keys={}", flowId, variables.keySet());
        return ApiResponse.ok(engine.updateContext(flowId, variables));
    }
}
