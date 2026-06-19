package com.aflow.agent.tool;

import com.aflow.common.annotation.FlowNode;
import com.aflow.common.executor.NodeExecutor;
import com.aflow.common.model.FlowContext;
import com.aflow.common.model.NodeResult;
import com.aflow.common.model.NodeConfig;
import com.aflow.common.util.JsonUtil;

import java.util.Map;

/**
 * 将 AFlow 的 NodeExecutor 适配为 LLM 可调用的 Tool。
 * <p>
 * 通过 {@link Tool} 注解，NodeExecutor 可以被 LLM 在 ReAct 循环中调用。
 * 适配器负责：
 * <ul>
 *   <li>将 LLM 传入的参数（JSON 字符串）转换为 NodeConfig 的 config</li>
 *   <li>执行 NodeExecutor 并将结果转换为 LLM 可理解的文本</li>
 *   <li>处理异常并返回错误信息给 LLM</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class NodeExecutorToolAdapter {

    private final NodeExecutor executor;
    private final String name;
    private final String description;
    private final String parameterSchema;

    public NodeExecutorToolAdapter(NodeExecutor executor) {
        this(executor, resolveName(executor), resolveDescription(executor), resolveParameterSchema(executor));
    }

    public NodeExecutorToolAdapter(NodeExecutor executor, String name, String description, String parameterSchema) {
        this.executor = executor;
        this.name = name;
        this.description = description;
        this.parameterSchema = parameterSchema != null && !parameterSchema.isBlank()
                ? parameterSchema
                : "{\"type\":\"object\",\"properties\":{}}";
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParameterSchema() {
        return parameterSchema;
    }

    /**
     * 执行 Tool。
     *
     * @param argsJson LLM 传入的参数（JSON 字符串）
     * @param context  流程上下文
     * @return 执行结果文本（返回给 LLM）
     */
    public String execute(String argsJson, FlowContext context) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = com.aflow.common.util.JsonUtil.fromJson(argsJson, Map.class);
            NodeConfig nodeConfig = new NodeConfig(resolveNodeType(), argsMap, null);

            NodeResult result = executor.execute(nodeConfig, context);

            if (result == null) {
                return "Tool executed successfully (no output)";
            }
            if (result.status() == null) {
                return "Tool execution failed: missing status";
            }
            if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
                return "Tool execution failed: " + result.errorMessage();
            }
            if (result.outputs() != null && !result.outputs().isEmpty()) {
                return JsonUtil.toJson(result.outputs());
            }
            return "Tool executed successfully (status: " + result.status() + ")";
        } catch (Exception e) {
            return "Tool execution failed: " + e.getMessage();
        }
    }

    public NodeExecutor getExecutor() {
        return executor;
    }

    private String resolveNodeType() {
        FlowNode flowNode = executor.getClass().getAnnotation(FlowNode.class);
        return flowNode != null ? flowNode.type() : name;
    }

    private static String resolveName(NodeExecutor executor) {
        Tool toolAnnotation = executor.getClass().getAnnotation(Tool.class);
        if (toolAnnotation != null && !toolAnnotation.name().isBlank()) {
            return toolAnnotation.name();
        }
        FlowNode flowNode = executor.getClass().getAnnotation(FlowNode.class);
        if (flowNode != null && !flowNode.type().isBlank()) {
            return flowNode.type();
        }
        throw new IllegalArgumentException("NodeExecutor " + executor.getClass().getName() + " 缺少 @FlowNode");
    }

    private static String resolveDescription(NodeExecutor executor) {
        Tool toolAnnotation = executor.getClass().getAnnotation(Tool.class);
        if (toolAnnotation != null && !toolAnnotation.description().isBlank()) {
            return toolAnnotation.description();
        }
        FlowNode flowNode = executor.getClass().getAnnotation(FlowNode.class);
        if (flowNode != null && !flowNode.description().isBlank()) {
            return flowNode.description();
        }
        return "AFlow node executor tool";
    }

    private static String resolveParameterSchema(NodeExecutor executor) {
        Tool toolAnnotation = executor.getClass().getAnnotation(Tool.class);
        if (toolAnnotation != null && !toolAnnotation.parameters().isBlank()) {
            return toolAnnotation.parameters();
        }
        return "{\"type\":\"object\",\"properties\":{}}";
    }
}
