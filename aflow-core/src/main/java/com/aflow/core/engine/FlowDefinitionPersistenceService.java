package com.aflow.core.engine;

import com.aflow.common.model.FlowDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Persistence interface for flow definitions.
 * Implemented in the {@code aflow-persistence} module.
 */
public interface FlowDefinitionPersistenceService {

    void save(String id, String name, int version, String status, String dslContent);

    Optional<FlowDefinition> findById(String id);

    String findDslContentById(String id);

    List<FlowDefinition> findAll();

    void updateDsl(String id, String name, String dslContent);

    void updateStatus(String id, String status);

    void delete(String id);
}
