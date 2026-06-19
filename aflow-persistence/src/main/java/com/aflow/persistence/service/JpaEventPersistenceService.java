package com.aflow.persistence.service;

import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventType;
import com.aflow.persistence.entity.FlowEventEntity;
import com.aflow.persistence.repository.FlowEventRepository;
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
public class JpaEventPersistenceService implements EventPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(JpaEventPersistenceService.class);
    private final FlowEventRepository repository;

    public JpaEventPersistenceService(FlowEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveEvent(String flowInstanceId, String nodeId, FlowEventType eventType,
                          String eventData, long durationMs) {
        FlowEventEntity entity = new FlowEventEntity();
        entity.setFlowInstanceId(flowInstanceId);
        entity.setNodeId(nodeId);
        entity.setEventType(eventType.name());
        entity.setEventData(eventData);
        entity.setDurationMs(durationMs);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
        log.debug("Event saved: flow={}, node={}, type={}", flowInstanceId, nodeId, eventType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByFlowInstanceId(String flowInstanceId) {
        return repository.findByFlowInstanceIdOrderByCreatedAtAsc(flowInstanceId)
                .stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(FlowEventEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("flowInstanceId", entity.getFlowInstanceId());
        map.put("nodeId", entity.getNodeId());
        map.put("eventType", entity.getEventType());
        map.put("eventData", entity.getEventData());
        map.put("durationMs", entity.getDurationMs());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }
}
