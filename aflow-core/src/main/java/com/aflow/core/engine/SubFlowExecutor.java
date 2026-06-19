package com.aflow.core.engine;

import com.aflow.common.model.FlowContext;
import com.aflow.common.model.FlowDefinition;

/**
 * 子流程执行器接口。
 * <p>
 * 供循环节点（forEach / while）使用，在父流程的上下文中执行子 DAG。
 * 子流程与父流程共享同一个 {@link FlowContext}（变量空间），
 * 子流程中节点产生的输出会直接合并到父流程变量中。
 * <p>
 * 设计说明：将子流程执行逻辑从引擎主循环中剥离，
 * 避免修改 {@link DefaultWorkflowEngine#executeFlow} 的核心 DAG 遍历逻辑。
 * 循环节点内部管理迭代次数，每轮迭代调用此接口执行子 DAG。
 */
public interface SubFlowExecutor {

    /**
     * 在父流程上下文中执行一个子流程（子 DAG）。
     *
     * @param subFlow       子流程定义（包含 nodes 和 edges）
     * @param context       父流程的上下文（共享变量空间）
     * @param parentNodeId  触发子流程的父节点 ID（用于事件记录和快照关联）
     * @param iteration     当前迭代轮次（从 0 开始，用于事件记录区分不同轮次）
     */
    void execute(FlowDefinition subFlow, FlowContext context, String parentNodeId, int iteration);
}
