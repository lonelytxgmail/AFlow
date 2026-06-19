package com.aflow.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Publishes flow execution events to the persistence layer.
 * Wraps {@link EventPersistenceService} with logging and error handling.
 */
@Service
public class FlowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FlowEventPublisher.class);

    private final EventPersistenceService persistenceService;

    public FlowEventPublisher(EventPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void publish(String flowInstanceId, String nodeId, FlowEventType eventType,
                        String eventData, long durationMs) {
        try {
            persistenceService.saveEvent(flowInstanceId, nodeId, eventType, eventData, durationMs);
            log.debug("Event published: flow={}, node={}, type={}", flowInstanceId, nodeId, eventType);
        } catch (Exception e) {
            log.error("Failed to publish event: flow={}, type={}, error={}",
                    flowInstanceId, eventType, e.getMessage(), e);
        }
    }
}
