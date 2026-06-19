package com.aflow.components.loop;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.*;
import com.aflow.core.engine.SubFlowExecutor;
import com.aflow.core.expression.ExpressionEvaluator;
import com.aflow.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ForEach 循环节点执行器。
 * <p>
 * 遍历一个集合，对每个元素执行一段子流程（子 DAG）。
 * <p>
 * <b>配置项：</b>
 * <pre>{@code
 * {
 *   "items": "#userList",              // SpEL 表达式，从上下文中解析出要遍历的集合
 *   "itemVar": "currentUser",          // 每轮迭代中当前元素的变量名（存入 context）
 *   "indexVar": "index",              // 可选：当前迭代索引的变量名（从 0 开始）
 *   "maxIterations": 1000,            // 可选：最大迭代次数，防止无限循环（默认 1000）
 *   "subFlow": {                       // 子流程定义（内嵌的 DAG）
 *     "nodes": [...],
 *     "edges": [...]
 *   }
 * }
 * }</pre>
 * <p>
 * <b>执行流程：</b>
 * <ol>
 *   <li>通过 SpEL 表达式从上下文解析出 items 集合</li>
 *   <li>对集合中的每个元素：
 *     <ul>
 *       <li>将元素存入 context[itemVar]</li>
 *       <li>将索引存入 context[indexVar]（如果配置了）</li>
 *       <li>调用 {@link SubFlowExecutor} 执行子 DAG</li>
 *     </ul>
 *   </li>
 *   <li>所有元素遍历完毕，返回成功</li>
 * </ol>
 * <p>
 * 子流程与父流程共享同一个 {@link FlowContext}，子流程中产生的变量变更
 * 会直接反映在父流程上下文中。
 */
@FlowNode(type = "forEach", name = "ForEach 循环", description = "遍历集合并对每个元素执行子流程")
@Component
public class ForEachNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ForEachNodeExecutor.class);

    /** 默认最大迭代次数，防止无限循环 */
    private static final int DEFAULT_MAX_ITERATIONS = 1000;

    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectProvider<SubFlowExecutor> subFlowExecutorProvider;

    public ForEachNodeExecutor(ExpressionEvaluator expressionEvaluator, ObjectProvider<SubFlowExecutor> subFlowExecutorProvider) {
        this.expressionEvaluator = expressionEvaluator;
        this.subFlowExecutorProvider = subFlowExecutorProvider;
    }

    @Override
    public NodeResult execute(NodeConfig config, FlowContext context) {
        Map<String, Object> cfg = config.getConfig();

        // ── 1. 解析配置参数 ──
        String itemsExpression = String.valueOf(cfg.getOrDefault("items", ""));
        String itemVar = String.valueOf(cfg.getOrDefault("itemVar", "item"));
        String indexVar = cfg.get("indexVar") != null ? String.valueOf(cfg.get("indexVar")) : null;
        int maxIterations = cfg.get("maxIterations") != null
                ? Integer.parseInt(String.valueOf(cfg.get("maxIterations")))
                : DEFAULT_MAX_ITERATIONS;

        if (itemsExpression.isBlank()) {
            return NodeResult.failed("forEach 节点缺少 items 配置（SpEL 表达式）");
        }

        // ── 2. 通过 SpEL 表达式解析出要遍历的集合 ──
        List<Object> items;
        try {
            Object rawItems = expressionEvaluator.evaluate(itemsExpression, context, Object.class);
            items = toList(rawItems);
        } catch (Exception e) {
            log.error("forEach 解析 items 表达式失败: '{}', error={}", itemsExpression, e.getMessage());
            return NodeResult.failed("forEach 解析 items 失败: " + e.getMessage());
        }

        if (items.isEmpty()) {
            log.info("forEach 集合为空，跳过执行: itemVar={}", itemVar);
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("forEachCount", 0);
            return NodeResult.success(outputs);
        }

        // 安全检查：迭代次数不超过上限
        if (items.size() > maxIterations) {
            return NodeResult.failed("forEach 迭代次数 (" + items.size() + ") 超过最大限制 (" + maxIterations + ")");
        }

        // ── 3. 解析子流程定义 ──
        FlowDefinition subFlow = parseSubFlow(cfg.get("subFlow"));
        if (subFlow == null) {
            return NodeResult.failed("forEach 节点缺少 subFlow 配置（子流程定义）");
        }

        // ── 4. 逐个元素执行子流程 ──
        String nodeId = context.getCurrentNodeId();
        log.info("forEach 开始迭代: node={}, itemsCount={}, itemVar={}", nodeId, items.size(), itemVar);

        for (int i = 0; i < items.size(); i++) {
            // 将当前元素和索引存入上下文
            context.putVariable(itemVar, items.get(i));
            if (indexVar != null) {
                context.putVariable(indexVar, i);
            }

            log.debug("forEach 迭代: node={}, iteration={}/{}, item={}", nodeId, i + 1, items.size(), items.get(i));

            // 调用子流程执行器执行子 DAG
            subFlowExecutorProvider.getObject().execute(subFlow, context, nodeId, i);
        }

        // ── 5. 返回执行结果 ──
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("forEachCount", items.size());
        outputs.put("forEachItems", items);

        log.info("forEach 迭代完成: node={}, totalIterations={}", nodeId, items.size());
        return NodeResult.success(outputs);
    }

    /**
     * 从配置中解析子流程定义。
     * subFlow 可以是一个完整的 FlowDefinition JSON 对象（含 nodes 和 edges）。
     */
    @SuppressWarnings("unchecked")
    private FlowDefinition parseSubFlow(Object subFlowConfig) {
        if (subFlowConfig == null) return null;
        try {
            String json = JsonUtil.toJson(subFlowConfig);
            FlowDefinition subFlow = JsonUtil.fromJson(json, FlowDefinition.class);
            // 子流程不需要 id/name/version，只要有 nodes 和 edges 即可
            if (subFlow.getNodes() == null || subFlow.getNodes().isEmpty()) {
                return null;
            }
            return subFlow;
        } catch (Exception e) {
            log.error("解析子流程定义失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将各种集合类型统一转为 List。
     * 支持：List、Set、数组、Map.values() 等。
     */
    @SuppressWarnings("unchecked")
    private List<Object> toList(Object rawItems) {
        if (rawItems == null) return Collections.emptyList();
        if (rawItems instanceof List<?>) return new ArrayList<>((List<Object>) rawItems);
        if (rawItems instanceof Set<?>) return new ArrayList<>((Set<Object>) rawItems);
        if (rawItems instanceof Collection<?>) return new ArrayList<>((Collection<Object>) rawItems);
        if (rawItems.getClass().isArray()) {
            Object[] array = (Object[]) rawItems;
            return Arrays.asList(array);
        }
        // 单个对象包装为单元素列表
        return List.of(rawItems);
    }
}
