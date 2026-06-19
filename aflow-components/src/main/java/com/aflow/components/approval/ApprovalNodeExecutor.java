package com.aflow.components.approval;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.model.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审批节点执行器。
 * <p>
 * 执行时将审批请求信息写入 FlowContext 并返回 SUSPENDED 状态，
 * 等待人工审批后由 ApprovalService 触发流程恢复。
 * <p>
 * Config 结构:
 * <pre>
 * {
 *   "title": "请确认部署到生产环境",
 *   "description": "本次部署包含 3 个服务的更新",
 *   "options": [{"label": "批准", "value": "approve"}, {"label": "拒绝", "value": "reject"}],
 *   "timeoutMinutes": 60,
 *   "timeoutAction": "reject"   // approve | reject | escalate
 * }
 * </pre>
 */
@FlowNode(type = "approval", name = "Human Approval", description = "Suspend flow and wait for human approval")
public class ApprovalNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ApprovalNodeExecutor.class);

    private static final int DEFAULT_TIMEOUT_MINUTES = 1440; // 24 hours
    private static final String DEFAULT_TIMEOUT_ACTION = "reject";

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();

        String title = getStringOrDefault(cfg, "title", "Approval Required");
        String description = getStringOrDefault(cfg, "description", "");
        Object options = cfg.get("options");
        int timeoutMinutes = getIntOrDefault(cfg, "timeoutMinutes", DEFAULT_TIMEOUT_MINUTES);
        String timeoutAction = getStringOrDefault(cfg, "timeoutAction", DEFAULT_TIMEOUT_ACTION);

        // Generate approval request ID
        String approvalRequestId = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(timeoutMinutes, ChronoUnit.MINUTES);

        // Write approval request info into FlowContext for downstream consumption
        Map<String, Object> approvalInfo = new HashMap<>();
        approvalInfo.put("approvalRequestId", approvalRequestId);
        approvalInfo.put("flowId", context.getFlowInstanceId());
        approvalInfo.put("nodeId", context.getCurrentNodeId());
        approvalInfo.put("title", title);
        approvalInfo.put("description", description);
        approvalInfo.put("options", options);
        approvalInfo.put("timeoutMinutes", timeoutMinutes);
        approvalInfo.put("timeoutAction", timeoutAction);
        approvalInfo.put("deadline", deadline.toString());
        approvalInfo.put("status", "PENDING");

        context.putVariable("_approvalRequest", approvalInfo);

        log.info("Approval node suspending flow: flowId={}, nodeId={}, requestId={}, deadline={}",
                context.getFlowInstanceId(), context.getCurrentNodeId(), approvalRequestId, deadline);

        return NodeResult.suspended();
    }

    private String getStringOrDefault(Map<String, Object> cfg, String key, String defaultValue) {
        Object value = cfg.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private int getIntOrDefault(Map<String, Object> cfg, String key, int defaultValue) {
        Object value = cfg.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
