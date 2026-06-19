package com.aflow.agent.tool;

import com.aflow.common.model.FlowContext;

import java.util.List;

/**
 * 动态 Tool 选择器接口。
 * <p>
 * 根据 Agent 节点的 tools 配置和运行时上下文，决定本次 ReAct 循环中可用的 Tool 列表。
 * 支持三种模式：
 * <ul>
 *   <li><b>静态列表</b>：["tool_a", "tool_b"] — 现有行为</li>
 *   <li><b>SpEL 表达式</b>：根据上下文动态计算 Tool 列表</li>
 *   <li><b>全量</b>："*" 或空 — 暴露所有已发布 Tool</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.1.0
 */
public interface ToolSelector {

    /**
     * 根据配置和上下文选择可用的 Tool 列表。
     *
     * @param toolsConfig Agent 节点 config 中的 tools 字段值
     * @param context     当前流程上下文
     * @param registry    Tool 注册表
     * @return 本次 Agent 执行可调用的 Tool 列表
     */
    List<NodeExecutorToolAdapter> selectTools(Object toolsConfig, FlowContext context, ToolRegistry registry);
}
