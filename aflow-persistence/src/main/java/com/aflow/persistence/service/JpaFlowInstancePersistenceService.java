package com.aflow.persistence.service;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;
import com.aflow.core.engine.FlowInstancePersistenceService;
import com.aflow.persistence.entity.FlowInstanceEntity;
import com.aflow.persistence.repository.FlowInstanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class JpaFlowInstancePersistenceService implements FlowInstancePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(JpaFlowInstancePersistenceService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Set<String>> STRING_SET_TYPE = new TypeReference<>() {};

    private final FlowInstanceRepository repository;
    private final ObjectMapper objectMapper;

    public JpaFlowInstancePersistenceService(FlowInstanceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(FlowContext context) {
        try {
            FlowInstanceEntity entity = repository.findById(context.getFlowInstanceId())
                    .orElse(new FlowInstanceEntity());
            entity.setId(context.getFlowInstanceId());
            entity.setDefinitionId(context.getFlowDefinitionId());
            entity.setStatus(context.getStatus().name());
            entity.setCurrentNodeId(context.getCurrentNodeId());
            entity.setVariables(objectMapper.writeValueAsString(context.getVariables()));
            entity.setMetadata(objectMapper.writeValueAsString(context.getMetadata()));
            entity.setExecutionPath(objectMapper.writeValueAsString(context.getExecutionPath()));
            entity.setBreakpoints(objectMapper.writeValueAsString(context.getBreakpoints()));
            entity.setDebugMode(context.isDebugMode());
            entity.setTriggerType("MANUAL");

            if (context.getStatus() == FlowStatus.RUNNING && entity.getStartedAt() == null) {
                entity.setStartedAt(LocalDateTime.now());
            }
            if (context.getStatus() == FlowStatus.COMPLETED || context.getStatus() == FlowStatus.FAILED
                    || context.getStatus() == FlowStatus.CANCELLED) {
                entity.setCompletedAt(LocalDateTime.now());
            }

            repository.save(entity);
            log.debug("Saved flow instance: id={}, status={}", context.getFlowInstanceId(), context.getStatus());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize flow context", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowContext> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowContext> findByDefinitionId(String definitionId) {
        return repository.findByDefinitionId(definitionId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowContext> findByStatus(FlowStatus status) {
        return repository.findByStatus(status.name()).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowContext> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private FlowContext toDomain(FlowInstanceEntity entity) {
        FlowContext context = new FlowContext(entity.getId(), entity.getDefinitionId());
        context.setStatus(FlowStatus.valueOf(entity.getStatus()));
        context.setCurrentNodeId(entity.getCurrentNodeId());

        try {
            if (entity.getVariables() != null) {
                Map<String, Object> vars = objectMapper.readValue(entity.getVariables(), MAP_TYPE);
                context.setVariables(new HashMap<>(vars));
            }
            if (entity.getMetadata() != null) {
                Map<String, Object> meta = objectMapper.readValue(entity.getMetadata(), MAP_TYPE);
                context.setMetadata(new HashMap<>(meta));
            }
            if (entity.getExecutionPath() != null) {
                List<String> path = objectMapper.readValue(entity.getExecutionPath(), STRING_LIST_TYPE);
                context.setExecutionPath(new ArrayList<>(path));
            }
            if (entity.getBreakpoints() != null) {
                Set<String> bps = objectMapper.readValue(entity.getBreakpoints(), STRING_SET_TYPE);
                context.setBreakpoints(new HashSet<>(bps));
            }
            context.setDebugMode(entity.isDebugMode());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize flow instance data: {}", e.getMessage());
        }

        return context;
    }
}
