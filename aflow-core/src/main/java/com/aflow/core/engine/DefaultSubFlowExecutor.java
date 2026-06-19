package com.aflow.core.engine;

import com.aflow.common.exception.FlowExecutionException;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.*;
import com.aflow.common.util.JsonUtil;
import com.aflow.core.event.EventPersistenceService;
import com.aflow.core.event.FlowEventType;
import com.aflow.core.expression.ExpressionEvaluator;
import com.aflow.core.registry.NodeRegistry;
import com.aflow.core.snapshot.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 子流程执行器默认实现。
 * <p>
 * 在父流程的 {@link FlowContext} 中执行子 DAG，核心逻辑：
 * <ol>
 *   <li>找到子 DAG 的起始节点（无入边的节点）</li>
 *   <li>依次执行节点，通过条件边决定下一个节点</li>
 *   <li>每个节点执行前后保存快照、发布事件（与主引擎行为一致）</li>
 *   <li>到达终端节点（无出边）时，子流程结束</li>
 * </ol>
 * <p>
 * 与主引擎的区别：
 * <ul>
 *   <li>不支持断点和单步调试（循环节点内部对用户透明）</li>
 *   <li>不支持嵌套挂起（子流程中的节点如果返回 SUSPENDED 则视为失败）</li>
 *   <li>事件和快照中携带 parentNodeId 和 iteration 信息以便追踪</li>
 * </ul>
 */
@Service
public class DefaultSubFlowExecutor implements SubFlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubFlowExecutor.class);

    private final NodeRegistry nodeRegistry;
    private final ExpressionEvaluator expressionEvaluator;
    private final SnapshotManager snapshotManager;
    private final EventPersistenceService eventPersistenceService;

    public DefaultSubFlowExecutor(NodeRegistry nodeRegistry,
                                   ExpressionEvaluator expressionEvaluator,
                                   SnapshotManager snapshotManager,
                                   EventPersistenceService eventPersistenceService) {
        this.nodeRegistry = nodeRegistry;
        this.expressionEvaluator = expressionEvaluator;
        this.snapshotManager = snapshotManager;
        this.eventPersistenceService = eventPersistenceService;
    }

    @Override
    public void execute(FlowDefinition subFlow, FlowContext context, String parentNodeId, int iteration) {
        if (subFlow == null || subFlow.getNodes() == null || subFlow.getNodes().isEmpty()) {
            log.warn("子流程为空，跳过执行: parentNode={}, iteration={}", parentNodeId, iteration);
            return;
        }

        String flowInstanceId = context.getFlowInstanceId();
        log.info("子流程开始执行: flow={}, parentNode={}, iteration={}, nodes={}",
                flowInstanceId, parentNodeId, iteration, subFlow.getNodes().size());

        // 找到子 DAG 的起始节点
        String currentNodeId = findStartNode(subFlow);
        if (currentNodeId == null) {
            log.error("子流程未找到起始节点: parentNode={}", parentNodeId);
            throw new FlowExecutionException("子流程未找到起始节点: parentNode=" + parentNodeId);
        }

        // 遍历执行子 DAG
        Set<String> visitedNodes = new HashSet<>();
        int safetyCounter = 0;
        int maxNodes = subFlow.getNodes().size() * 100; // 安全上限，防止异常情况下的无限循环

        while (currentNodeId != null) {
            // 安全检查：防止节点重复执行导致的无限循环
            if (safetyCounter++ > maxNodes) {
                log.error("子流程执行超出安全上限: parentNode={}, counter={}", parentNodeId, safetyCounter);
                throw new FlowExecutionException("子流程执行超出安全上限: " + parentNodeId);
            }

            NodeDefinition nodeDef = findNodeDefinition(currentNodeId, subFlow);
            if (nodeDef == null) {
                throw new FlowExecutionException("子流程中未找到节点: " + currentNodeId);
            }

            // 执行子流程中的单个节点
            NodeResult result = executeSubNode(flowInstanceId, nodeDef, context, parentNodeId, iteration);

            if (result.status() == ResultStatus.FAILED) {
                throw new FlowExecutionException("子流程节点执行失败: node=" + currentNodeId
                        + ", error=" + result.errorMessage());
            }

            // 子流程中的节点不支持 SUSPENDED，视为异常
            if (result.status() == ResultStatus.SUSPENDED) {
                throw new FlowExecutionException("子流程中不支持挂起操作: node=" + currentNodeId);
            }

            // 合并节点输出到上下文
            if (result.outputs() != null) {
                if (nodeDef.getOutput() != null && !nodeDef.getOutput().isBlank()) {
                    context.putVariable(nodeDef.getOutput(), result.outputs());
                }
                context.mergeOutputs(result.outputs());
            }

            // 确定子流程中的下一个节点
            if (result.nextNodeId() != null) {
                currentNodeId = result.nextNodeId();
            } else {
                currentNodeId = determineNextNode(currentNodeId, subFlow, context);
            }
        }

        log.info("子流程执行完成: flow={}, parentNode={}, iteration={}", flowInstanceId, parentNodeId, iteration);
    }

    /**
     * 执行子流程中的单个节点：快照 + 事件 + 执行器调用。
     * 逻辑与 {@link DefaultWorkflowEngine#executeNode} 类似，但简化了断点/调试处理。
     */
    private NodeResult executeSubNode(String flowInstanceId, NodeDefinition nodeDef,
                                       FlowContext context, String parentNodeId, int iteration) {
        String nodeId = nodeDef.getId();
        long startTime = System.currentTimeMillis();

        log.debug("子流程节点执行: flow={}, node={}, type={}, parentNode={}, iteration={}",
                flowInstanceId, nodeId, nodeDef.getType(), parentNodeId, iteration);

        // 记录执行路径
        context.recordNodeExecution(nodeId);

        // 保存执行前快照
        snapshotManager.saveSnapshot(flowInstanceId, nodeId + "#" + parentNodeId + "#" + iteration,
                SnapshotPhase.BEFORE, context);

        // 发布节点进入事件（eventData 中携带子流程信息以便区分）
        eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ENTER,
                JsonUtil.toJson(Map.of("type", nodeDef.getType(),
                        "subFlowParent", parentNodeId,
                        "iteration", iteration)), 0);

        try {
            NodeExecutor executor = nodeRegistry.getExecutor(nodeDef.getType());

            NodeConfig config = new NodeConfig();
            config.setType(nodeDef.getType());
            config.setConfig(nodeDef.getConfig() != null ? nodeDef.getConfig() : Map.of());
            config.setOutputVariable(nodeDef.getOutput());

            NodeResult result = executor.execute(config, context);
            long duration = System.currentTimeMillis() - startTime;

            // 保存执行后快照
            snapshotManager.saveSnapshot(flowInstanceId, nodeId + "#" + parentNodeId + "#" + iteration,
                    SnapshotPhase.AFTER, context);

            // 发布节点退出/错误事件
            if (result.status() == ResultStatus.SUCCESS) {
                eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_EXIT,
                        JsonUtil.toJson(Map.of("outputKeys", result.outputs() != null ? result.outputs().keySet() : Set.of(),
                                "subFlowParent", parentNodeId,
                                "iteration", iteration)), duration);
            } else if (result.status() == ResultStatus.FAILED) {
                eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ERROR,
                        JsonUtil.toJson(Map.of("error", result.errorMessage() != null ? result.errorMessage() : "unknown",
                                "subFlowParent", parentNodeId,
                                "iteration", iteration)), duration);
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("子流程节点执行异常: flow={}, node={}, error={}", flowInstanceId, nodeId, e.getMessage(), e);
            eventPersistenceService.saveEvent(flowInstanceId, nodeId, FlowEventType.NODE_ERROR,
                    JsonUtil.toJson(Map.of("error", e.getMessage(),
                            "subFlowParent", parentNodeId,
                            "iteration", iteration)), duration);
            return NodeResult.failed("子流程节点执行异常: " + e.getMessage());
        }
    }

    // ─── DAG 导航辅助方法 ──────────────────────────────────────────

    /**
     * 查找子 DAG 的起始节点（无入边的节点）。
     */
    private String findStartNode(FlowDefinition subFlow) {
        Set<String> nodesWithIncoming = new HashSet<>();
        if (subFlow.getEdges() != null) {
            subFlow.getEdges().forEach(e -> nodesWithIncoming.add(e.getTo()));
        }
        return subFlow.getNodes().stream()
                .map(NodeDefinition::getId)
                .filter(id -> !nodesWithIncoming.contains(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 在子 DAG 中确定下一个要执行的节点（支持条件边 SpEL 评估）。
     * 逻辑与 {@link DefaultWorkflowEngine#determineNextNode} 一致。
     */
    private String determineNextNode(String currentNodeId, FlowDefinition subFlow, FlowContext context) {
        if (subFlow.getEdges() == null) return null;

        List<EdgeDefinition> outgoingEdges = subFlow.getEdges().stream()
                .filter(e -> e.getFrom().equals(currentNodeId))
                .toList();

        if (outgoingEdges.isEmpty()) return null;

        // 单条无条件边直接跟随
        if (outgoingEdges.size() == 1 && (outgoingEdges.getFirst().getCondition() == null
                || outgoingEdges.getFirst().getCondition().isBlank())) {
            return outgoingEdges.getFirst().getTo();
        }

        // 多条边：逐一评估条件
        for (EdgeDefinition edge : outgoingEdges) {
            if (edge.getCondition() == null || edge.getCondition().isBlank()) continue;
            try {
                Boolean result = expressionEvaluator.evaluate(edge.getCondition(), context, Boolean.class);
                if (Boolean.TRUE.equals(result)) {
                    return edge.getTo();
                }
            } catch (Exception e) {
                log.warn("子流程边条件评估失败 '{}': {}", edge.getCondition(), e.getMessage());
            }
        }

        // 回退到第一条无条件边
        return outgoingEdges.stream()
                .filter(e -> e.getCondition() == null || e.getCondition().isBlank())
                .map(EdgeDefinition::getTo)
                .findFirst()
                .orElse(null);
    }

    private NodeDefinition findNodeDefinition(String nodeId, FlowDefinition subFlow) {
        return subFlow.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }
}
