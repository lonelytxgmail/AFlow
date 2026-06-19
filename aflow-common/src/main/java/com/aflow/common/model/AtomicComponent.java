package com.aflow.common.model;

import java.time.LocalDateTime;

/**
 * 原子能力组件。
 * <p>
 * 存储可复用的原子能力模板。每个原子能力封装了一个特定功能（如发送邮件、查询数据库），
 * 用户可以通过 API 创建、编辑、删除原子能力，并在流程 DSL 中引用。
 * <p>
 * <b>使用场景：</b>
 * <ul>
 *   <li>用户创建一个"发送告警邮件"原子能力，配置 SMTP 参数模板</li>
 *   <li>在流程 DSL 中引用该原子能力，传入收件人和内容即可使用</li>
 *   <li>多个流程可共享同一个原子能力，修改模板后所有引用处生效</li>
 * </ul>
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public class AtomicComponent {

    /** 原子能力唯一标识 */
    private String id;

    /** 原子能力名称（如 "发送告警邮件"） */
    private String name;

    /** 功能描述 */
    private String description;

    /** 分类（如 "http"、"transform"、"notification"） */
    private String category;

    /** 底层节点执行器类型（如 "http"、"script"、"assign"） */
    private String nodeType;

    /** 节点配置模板（JSON），定义默认参数结构 */
    private String configTemplate;

    /** 输入参数 JSON Schema（用于验证调用方传入的参数） */
    private String inputSchema;

    /** 输出结果 JSON Schema（用于验证节点输出） */
    private String outputSchema;

    /** 图标标识（前端展示用） */
    private String icon;

    /** 状态：DRAFT / PUBLISHED / ARCHIVED */
    private String status;

    /** 创建者 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    public AtomicComponent() {}

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getConfigTemplate() { return configTemplate; }
    public void setConfigTemplate(String configTemplate) { this.configTemplate = configTemplate; }

    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }

    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "AtomicComponent{id='" + id + "', name='" + name + "', nodeType='" + nodeType +
                "', category='" + category + "', status='" + status + "'}";
    }
}
