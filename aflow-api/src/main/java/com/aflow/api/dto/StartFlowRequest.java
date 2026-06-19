package com.aflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record StartFlowRequest(@NotBlank(message = "flowDefinitionId is required") String flowDefinitionId, Map<String, Object> inputs) {}
