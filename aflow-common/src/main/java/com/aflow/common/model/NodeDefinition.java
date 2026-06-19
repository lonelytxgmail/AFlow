package com.aflow.common.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Definition of a single node within a flow.
 * <p>
 * 节点定义包含：唯一标识(id)、类型(type)、显示名称(name)、
 * 运行时配置(config)、输出变量名(output)、静态断点(breakpoint)。
 * 其中 name 和 ui 坐标信息由前端编辑器使用，引擎日志和事件中也会引用 name 以提高可读性。
 */
public class NodeDefinition {

    /** 节点唯一标识（在同一流程内唯一） */
    private String id;

    /** 节点类型，对应 @FlowNode 注解的 type 属性（如 "http"、"condition"、"script"） */
    private String type;

    /** 节点显示名称，由前端编辑器设置，用于日志、事件、调试等场景的可读性 */
    private String name;

    /** 类型特定的配置参数，不同节点类型有不同的配置结构 */
    private Map<String, Object> config = new HashMap<>();

    /** 输出变量名：节点执行结果将以此名称存入 FlowContext 变量空间 */
    private String output;

    /** 是否在该节点上设置静态断点（执行到此节点前暂停） */
    private boolean breakpoint;

    public NodeDefinition() {
    }

    public NodeDefinition(String id, String type) {
        this.id = id;
        this.type = type;
    }

    // ─── Getters / Setters ──────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config != null ? config : new HashMap<>();
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public boolean isBreakpoint() {
        return breakpoint;
    }

    public void setBreakpoint(boolean breakpoint) {
        this.breakpoint = breakpoint;
    }

    @Override
    public String toString() {
        return "NodeDefinition{id='" + id + "', name='" + name + "', type='" + type + "', output='" + output + "'}";
    }
}
