package com.aflow.persistence.repository;

import com.aflow.persistence.entity.FlowEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for flow events.
 */
@Repository
public interface FlowEventRepository extends JpaRepository<FlowEventEntity, Long> {

    /**
     * Find all events for a flow instance, ordered by creation time.
     *
     * @param flowInstanceId the flow instance ID
     * @return list of events in chronological order
     */
    List<FlowEventEntity> findByFlowInstanceIdOrderByCreatedAtAsc(String flowInstanceId);

    /**
     * Find events for a specific node within a flow instance.
     *
     * @param flowInstanceId the flow instance ID
     * @param nodeId the node ID
     * @return list of matching events
     */
    List<FlowEventEntity> findByFlowInstanceIdAndNodeId(String flowInstanceId, String nodeId);
}
