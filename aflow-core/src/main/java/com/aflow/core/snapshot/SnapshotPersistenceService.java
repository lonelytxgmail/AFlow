package com.aflow.core.snapshot;

import com.aflow.common.model.SnapshotPhase;

import java.util.List;
import java.util.Map;

/**
 * Persistence interface for flow execution snapshots.
 * Implemented in the {@code aflow-persistence} module.
 */
public interface SnapshotPersistenceService {

    void saveSnapshot(String flowInstanceId, String nodeId, SnapshotPhase phase, String contextJson);

    List<Map<String, Object>> findByFlowInstanceId(String flowInstanceId);

    List<Map<String, Object>> findByFlowInstanceIdAndNodeId(String flowInstanceId, String nodeId);
}
