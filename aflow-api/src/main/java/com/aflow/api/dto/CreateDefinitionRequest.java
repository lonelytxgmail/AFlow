package com.aflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDefinitionRequest(String id, @NotBlank(message = "name is required") String name, @NotBlank(message = "dslContent is required") String dslContent) {}
