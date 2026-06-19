package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.common.model.FlowContext;
import com.aflow.core.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/callback")
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    private final WorkflowEngine engine;

    public CallbackController(WorkflowEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/{flowInstanceId}/{nodeId}")
    public ApiResponse<FlowContext> callback(@PathVariable String flowInstanceId,
                                             @PathVariable String nodeId,
                                             @RequestBody(required = false) Map<String, Object> data) {
        log.info("Callback received: flowInstanceId={}, nodeId={}", flowInstanceId, nodeId);
        // A real implementation would verify the nodeId matches the current suspended node,
        // and perhaps validate a callback token. Here we just resume.
        return ApiResponse.ok(engine.resume(flowInstanceId, data));
    }
}
