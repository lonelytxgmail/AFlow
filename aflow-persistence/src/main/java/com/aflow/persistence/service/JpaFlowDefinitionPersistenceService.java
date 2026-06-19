package com.aflow.persistence.service;

import com.aflow.common.model.FlowDefinition;
import com.aflow.core.engine.FlowDefinitionPersistenceService;
import com.aflow.persistence.entity.FlowDefinitionEntity;
import com.aflow.persistence.repository.FlowDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class JpaFlowDefinitionPersistenceService implements FlowDefinitionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(JpaFlowDefinitionPersistenceService.class);
    private final FlowDefinitionRepository repository;

    public JpaFlowDefinitionPersistenceService(FlowDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String id, String name, int version, String status, String dslContent) {
        FlowDefinitionEntity entity = repository.findById(id).orElse(new FlowDefinitionEntity());
        entity.setId(id);
        entity.setName(name);
        entity.setVersion(version);
        entity.setStatus(status);
        entity.setDslContent(dslContent);
        repository.save(entity);
        log.debug("Saved flow definition: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowDefinition> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public String findDslContentById(String id) {
        return repository.findById(id).map(FlowDefinitionEntity::getDslContent).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowDefinition> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void updateDsl(String id, String name, String dslContent) {
        FlowDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Definition not found: " + id));
        if (name != null) entity.setName(name);
        entity.setDslContent(dslContent);
        repository.save(entity);
    }

    @Override
    public void updateStatus(String id, String status) {
        FlowDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Definition not found: " + id));
        entity.setStatus(status);
        repository.save(entity);
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    private FlowDefinition toDomain(FlowDefinitionEntity entity) {
        FlowDefinition def = new FlowDefinition();
        def.setId(entity.getId());
        def.setName(entity.getName());
        def.setVersion(entity.getVersion());
        return def;
    }
}
