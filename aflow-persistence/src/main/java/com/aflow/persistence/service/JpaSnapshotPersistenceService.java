package com.aflow.persistence.service;

import com.aflow.common.model.SnapshotPhase;
import com.aflow.core.snapshot.SnapshotPersistenceService;
import com.aflow.persistence.entity.ContextSnapshotEntity;
import com.aflow.persistence.repository.ContextSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class JpaSnapshotPersistenceService implements SnapshotPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(JpaSnapshotPersistenceService.class);
    private final ContextSnapshotRepository repository;

    public JpaSnapshotPersistenceService(ContextSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveSnapshot(String flowInstanceId, String nodeId, SnapshotPhase phase, String contextJson) {
        ContextSnapshotEntity entity = new ContextSnapshotEntity();
        entity.setFlowInstanceId(flowInstanceId);
        entity.setNodeId(nodeId);
        entity.setPhase(phase.name());
        entity.setContextData(contextJson);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
        log.debug("Snapshot saved: flow={}, node={}, phase={}", flowInstanceId, nodeId, phase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByFlowInstanceId(String flowInstanceId) {
        return repository.findByFlowInstanceIdOrderByCreatedAtAsc(flowInstanceId)
                .stream().map(this::toMap).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByFlowInstanceIdAndNodeId(String flowInstanceId, String nodeId) {
        return repository.findByFlowInstanceIdAndNodeId(flowInstanceId, nodeId)
                .stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(ContextSnapshotEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("flowInstanceId", entity.getFlowInstanceId());
        map.put("nodeId", entity.getNodeId());
        map.put("phase", entity.getPhase());
        map.put("contextData", entity.getContextData());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }
}
