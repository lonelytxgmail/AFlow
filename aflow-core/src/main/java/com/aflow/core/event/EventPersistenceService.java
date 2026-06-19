package com.aflow.core.event;

import java.util.List;
import java.util.Map;

/**
 * Persistence interface for flow execution events.
 * Implemented in the {@code aflow-persistence} module.
 */
public interface EventPersistenceService {

    void saveEvent(String flowInstanceId, String nodeId, FlowEventType eventType,
                   String eventData, long durationMs);

    List<Map<String, Object>> findByFlowInstanceId(String flowInstanceId);
}
