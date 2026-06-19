package com.aflow.agent.executor;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent Tool 调用频率限制器。
 * <p>
 * 在单次 Agent ReAct 循环中，限制每个 Tool 的最大调用次数和所有 Tool 的总调用次数。
 * 超限时返回拒绝消息给 LLM，不实际执行 Tool。
 *
 * @author AFlow Team
 * @since 1.1.0
 */
public class ToolRateLimiter {

    private final int maxCallsPerTool;
    private final int maxTotalCalls;
    private final Map<String, Integer> perToolCounts = new HashMap<>();
    private int totalCalls = 0;

    /**
     * @param maxCallsPerTool 每个 Tool 最大调用次数（<=0 表示无限制）
     * @param maxTotalCalls   所有 Tool 总调用次数上限（<=0 表示无限制）
     */
    public ToolRateLimiter(int maxCallsPerTool, int maxTotalCalls) {
        this.maxCallsPerTool = maxCallsPerTool;
        this.maxTotalCalls = maxTotalCalls;
    }

    /**
     * 无限制的频率限制器（所有调用均放行）。
     */
    public static ToolRateLimiter unlimited() {
        return new ToolRateLimiter(0, 0);
    }

    /**
     * 检查是否允许调用该 Tool，如果允许则自增计数。
     *
     * @param toolName Tool 名称
     * @return true 表示允许调用，false 表示已超限
     */
    public boolean checkAndIncrement(String toolName) {
        // 检查总调用次数
        if (maxTotalCalls > 0 && totalCalls >= maxTotalCalls) {
            return false;
        }
        // 检查单 Tool 调用次数
        if (maxCallsPerTool > 0) {
            int count = perToolCounts.getOrDefault(toolName, 0);
            if (count >= maxCallsPerTool) {
                return false;
            }
        }
        // 通过检查，自增计数
        totalCalls++;
        perToolCounts.merge(toolName, 1, Integer::sum);
        return true;
    }

    /**
     * 获取超限拒绝消息。
     */
    public String getRejectionMessage(String toolName) {
        if (maxTotalCalls > 0 && totalCalls >= maxTotalCalls) {
            return "Tool call rejected: total call limit reached (" + totalCalls + "/" + maxTotalCalls + "). " +
                    "Please provide your final answer based on the information already gathered.";
        }
        int count = perToolCounts.getOrDefault(toolName, 0);
        return "Tool '" + toolName + "' call rejected: per-tool limit reached (" + count + "/" + maxCallsPerTool + "). " +
                "Try a different tool or provide your final answer.";
    }

    /**
     * 是否启用了频率限制。
     */
    public boolean isEnabled() {
        return maxCallsPerTool > 0 || maxTotalCalls > 0;
    }

    /**
     * 获取某 Tool 当前调用次数。
     */
    public int getCallCount(String toolName) {
        return perToolCounts.getOrDefault(toolName, 0);
    }

    /**
     * 获取总调用次数。
     */
    public int getTotalCalls() {
        return totalCalls;
    }
}
