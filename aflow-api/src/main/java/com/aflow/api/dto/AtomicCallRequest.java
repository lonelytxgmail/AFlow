package com.aflow.api.dto;

import java.util.Map;

public record AtomicCallRequest(Map<String, Object> config, Map<String, Object> inputs) {}
