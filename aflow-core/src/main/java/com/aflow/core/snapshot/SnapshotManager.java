package com.aflow.core.snapshot;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.SnapshotPhase;
import com.aflow.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages flow execution snapshots.
 * <p>
 * Before and after each node execution, the engine calls this manager to
 * deep-clone the flow context and persist the snapshot via
 * {@link SnapshotPersistenceService}.
 */
@Service
public class SnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);

    private final SnapshotPersistenceService persistenceService;

    public SnapshotManager(SnapshotPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Save a snapshot of the current flow context state.
     *
     * @param flowInstanceId the flow instance ID
     * @param nodeId         the node being executed
     * @param phase          BEFORE or AFTER execution
     * @param context        the flow context to snapshot
     */
    public void saveSnapshot(String flowInstanceId, String nodeId, SnapshotPhase phase, FlowContext context) {
        try {
            String contextJson = JsonUtil.toJson(context);
            persistenceService.saveSnapshot(flowInstanceId, nodeId, phase, contextJson);
            log.debug("Snapshot saved: flow='{}', node='{}', phase={}", flowInstanceId, nodeId, phase);
        } catch (Exception e) {
            // Snapshots are non-critical — log the error but don't fail the execution
            log.error("Failed to save snapshot for flow='{}', node='{}', phase={}: {}",
                    flowInstanceId, nodeId, phase, e.getMessage(), e);
        }
    }

    /**
     * Deep-clone a flow context for snapshot purposes.
     *
     * @param context the context to clone
     * @return an independent deep copy
     */
    public FlowContext deepCloneContext(FlowContext context) {
        return JsonUtil.deepClone(context, FlowContext.class);
    }
}
