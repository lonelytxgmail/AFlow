package com.aflow.api.controller;

import com.aflow.api.dto.ApiResponse;
import com.aflow.api.dto.CreateDefinitionRequest;
import com.aflow.common.model.FlowDefinition;
import com.aflow.core.dsl.DslParser;
import com.aflow.core.dsl.FlowDefinitionValidator;
import com.aflow.core.dsl.FlowDefinitionValidator.ValidationError;
import com.aflow.core.engine.FlowDefinitionPersistenceService;
import com.aflow.core.engine.WorkflowEngine;
import com.aflow.persistence.entity.DefinitionVersionEntity;
import com.aflow.persistence.service.DefinitionVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/definitions")
public class DefinitionController {

    private static final Logger log = LoggerFactory.getLogger(DefinitionController.class);

    private final FlowDefinitionPersistenceService definitionService;
    private final DslParser dslParser;
    private final FlowDefinitionValidator validator;
    private final WorkflowEngine engine;
    private final DefinitionVersionService versionService;

    public DefinitionController(FlowDefinitionPersistenceService definitionService,
                                DslParser dslParser,
                                FlowDefinitionValidator validator,
                                WorkflowEngine engine,
                                DefinitionVersionService versionService) {
        this.definitionService = definitionService;
        this.dslParser = dslParser;
        this.validator = validator;
        this.engine = engine;
        this.versionService = versionService;
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CreateDefinitionRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        log.info("Creating definition: id={}, name={}", id, request.name());
        // Validate DSL
        FlowDefinition parsed = dslParser.parse(request.dslContent());
        List<ValidationError> validationErrors = validator.validate(parsed);
        if (!validationErrors.isEmpty()) {
            return ApiResponse.ok(Map.of(
                    "valid", false,
                    "errors", validationErrors
            ));
        }

        definitionService.save(id, request.name(), 1, "DRAFT", request.dslContent());
        return ApiResponse.ok(Map.of("id", id, "name", request.name(), "status", "DRAFT"));
    }

    @GetMapping
    public ApiResponse<List<FlowDefinition>> list() {
        return ApiResponse.ok(definitionService.findAll());
    }

    @GetMapping("/{id:[a-fA-F0-9\\-]{36}}")
    public ApiResponse<?> get(@PathVariable String id) {
        return definitionService.findById(id)
                .map(def -> {
                    String dsl = definitionService.findDslContentById(id);
                    return ApiResponse.ok(Map.of("definition", def, "dslContent", dsl != null ? dsl : ""));
                })
                .orElse(ApiResponse.error("Definition not found: " + id));
    }

    @PutMapping("/{id:[a-fA-F0-9\\-]{36}}")
    public ApiResponse<?> update(@PathVariable String id, @Valid @RequestBody CreateDefinitionRequest request) {
        log.info("Updating definition: id={}, name={}", id, request.name());
        if (request.dslContent() != null) {
            FlowDefinition parsed = dslParser.parse(request.dslContent());
            List<ValidationError> validationErrors = validator.validate(parsed);
            if (!validationErrors.isEmpty()) {
                return ApiResponse.ok(Map.of(
                        "valid", false,
                        "errors", validationErrors
                ));
            }
        }
        definitionService.updateDsl(id, request.name(), request.dslContent());
        engine.invalidateDefinitionCache(id);
        return ApiResponse.ok("Updated");
    }

    @DeleteMapping("/{id:[a-fA-F0-9\\-]{36}}")
    public ApiResponse<?> delete(@PathVariable String id) {
        log.info("Deleting definition: id={}", id);
        definitionService.delete(id);
        return ApiResponse.ok("Deleted");
    }

    /**
     * Validate a DSL definition without persisting. Returns a structured list of validation errors.
     */
    @PostMapping("/validate")
    public ApiResponse<?> validate(@RequestBody Map<String, String> body) {
        String dslContent = body.get("dslContent");
        if (dslContent == null || dslContent.isBlank()) {
            return ApiResponse.ok(Map.of("valid", false, "errors",
                    List.of(ValidationError.of("dslContent", "DSL_EMPTY", "DSL content must not be empty"))));
        }
        FlowDefinition parsed = dslParser.parse(dslContent);
        List<ValidationError> errors = validator.validate(parsed);
        return ApiResponse.ok(Map.of("valid", errors.isEmpty(), "errors", errors));
    }

    @PostMapping("/{id:[a-fA-F0-9\\-]{36}}/publish")
    public ApiResponse<?> publish(@PathVariable String id) {
        log.info("Publishing definition: id={}", id);
        definitionService.updateStatus(id, "PUBLISHED");
        // Auto-create version snapshot on publish
        DefinitionVersionEntity version = versionService.createVersionSnapshot(id);
        return ApiResponse.ok(Map.of("message", "Published", "version", version.getVersionNumber()));
    }

    // --- Version Management Endpoints ---

    @GetMapping("/{id:[a-fA-F0-9\\-]{36}}/versions")
    public ApiResponse<?> listVersions(@PathVariable String id) {
        List<DefinitionVersionEntity> versions = versionService.listVersions(id);
        List<Map<String, Object>> result = versions.stream().map(v -> Map.<String, Object>of(
                "versionNumber", v.getVersionNumber(),
                "createdAt", v.getCreatedAt().toString()
        )).toList();
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id:[a-fA-F0-9\\-]{36}}/versions/{versionNumber}")
    public ApiResponse<?> getVersion(@PathVariable String id, @PathVariable int versionNumber) {
        return versionService.getVersion(id, versionNumber)
                .map(v -> ApiResponse.ok(Map.of(
                        "versionNumber", v.getVersionNumber(),
                        "snapshotJson", v.getSnapshotJson(),
                        "createdAt", v.getCreatedAt().toString()
                )))
                .orElse(ApiResponse.error("Version not found: " + versionNumber));
    }

    @PostMapping("/{id:[a-fA-F0-9\\-]{36}}/versions/{versionNumber}/rollback")
    public ApiResponse<?> rollback(@PathVariable String id, @PathVariable int versionNumber) {
        log.info("Rolling back definition: id={}, to version={}", id, versionNumber);
        DefinitionVersionEntity newVersion = versionService.rollback(id, versionNumber);
        return ApiResponse.ok(Map.of(
                "message", "Rolled back successfully",
                "newVersion", newVersion.getVersionNumber()
        ));
    }
}
