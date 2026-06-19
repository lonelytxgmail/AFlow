package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.dto.ResumeFlowRequest;
import com.aflow.api.dto.StartFlowRequest;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.core.engine.FlowInstancePersistenceService;
import com.aflow.core.engine.WorkflowEngine;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.snapshot.SnapshotPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flows")
public class FlowController {

    private static final Logger log = LoggerFactory.getLogger(FlowController.class);

    private final WorkflowEngine engine;
    private final FlowInstancePersistenceService instanceService;
    private final SnapshotPersistenceService snapshotService;
    private final EventPersistenceService eventService;

    public FlowController(WorkflowEngine engine,
                          FlowInstancePersistenceService instanceService,
                          SnapshotPersistenceService snapshotService,
                          EventPersistenceService eventService) {
        this.engine = engine;
        this.instanceService = instanceService;
        this.snapshotService = snapshotService;
        this.eventService = eventService;
    }

    @PostMapping("/start")
    public ApiResponse<FlowContext> start(@Valid @RequestBody StartFlowRequest request) {
        log.info("Starting flow: definitionId={}", request.flowDefinitionId());
        FlowContext ctx = engine.start(request.flowDefinitionId(), request.inputs());
        return ApiResponse.ok(ctx);
    }

    @GetMapping
    public ApiResponse<List<FlowContext>> list(@RequestParam(required = false) String status) {
        log.info("Listing flow instances, status filter={}", status);
        List<FlowContext> instances;
        if (status != null && !status.isBlank()) {
            instances = instanceService.findByStatus(FlowStatus.valueOf(status.toUpperCase()));
        } else {
            instances = instanceService.findAll();
        }
        return ApiResponse.ok(instances);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        log.info("Getting flow instance: id={}", id);
        return instanceService.findById(id)
                .map(ctx -> ApiResponse.ok(ctx))
                .orElse(ApiResponse.error("Instance not found: " + id));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<FlowContext> resume(@PathVariable String id, @RequestBody(required = false) ResumeFlowRequest request) {
        log.info("Resuming flow: id={}", id);
        Map<String, Object> inputs = request != null ? request.additionalInputs() : null;
        return ApiResponse.ok(engine.resume(id, inputs));
    }

    @PostMapping("/{id}/retry/{nodeId}")
    public ApiResponse<FlowContext> retry(@PathVariable String id, @PathVariable String nodeId) {
        log.info("Retrying flow: id={}, nodeId={}", id, nodeId);
        return ApiResponse.ok(engine.retry(id, nodeId));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<FlowContext> cancel(@PathVariable String id) {
        log.info("Cancelling flow: id={}", id);
        return ApiResponse.ok(engine.cancel(id));
    }

    @GetMapping("/{id}/snapshots")
    public ApiResponse<List<Map<String, Object>>> snapshots(@PathVariable String id) {
        return ApiResponse.ok(snapshotService.findByFlowInstanceId(id));
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<Map<String, Object>>> events(@PathVariable String id) {
        return ApiResponse.ok(eventService.findByFlowInstanceId(id));
    }

    @GetMapping("/{id}/diff/{nodeId}")
    public ApiResponse<List<Map<String, Object>>> diff(@PathVariable String id, @PathVariable String nodeId) {
        return ApiResponse.ok(snapshotService.findByFlowInstanceIdAndNodeId(id, nodeId));
    }
}
