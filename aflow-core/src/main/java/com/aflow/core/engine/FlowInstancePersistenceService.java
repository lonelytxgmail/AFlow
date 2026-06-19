package com.aflow.core.engine;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowStatus;

import java.util.List;
import java.util.Optional;

/**
 * Persistence interface for flow instances.
 * Implemented in the {@code aflow-persistence} module.
 */
public interface FlowInstancePersistenceService {

    void save(FlowContext context);

    Optional<FlowContext> findById(String id);

    List<FlowContext> findByDefinitionId(String definitionId);

    List<FlowContext> findByStatus(FlowStatus status);

    List<FlowContext> findAll();
}
