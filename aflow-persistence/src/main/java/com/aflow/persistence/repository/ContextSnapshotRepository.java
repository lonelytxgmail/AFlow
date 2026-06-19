package com.aflow.persistence.repository;

import com.aflow.persistence.entity.ContextSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for context snapshots.
 */
@Repository
public interface ContextSnapshotRepository extends JpaRepository<ContextSnapshotEntity, Long> {

    /**
     * Find all snapshots for a flow instance, ordered by creation time.
     *
     * @param flowInstanceId the flow instance ID
     * @return list of snapshots in chronological order
     */
    List<ContextSnapshotEntity> findByFlowInstanceIdOrderByCreatedAtAsc(String flowInstanceId);

    /**
     * Find snapshots for a specific node within a flow instance.
     *
     * @param flowInstanceId the flow instance ID
     * @param nodeId the node ID
     * @return list of matching snapshots
     */
    List<ContextSnapshotEntity> findByFlowInstanceIdAndNodeId(String flowInstanceId, String nodeId);
}
