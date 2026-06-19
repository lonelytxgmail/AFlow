package com.aflow.persistence.entity;

import com.aflow.common.model.AtomicComponent;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 原子能力 JPA 实体。
 * <p>
 * 对应 atomic_component 表，负责数据库映射。
 * 与 {@link AtomicComponent} 模型之间的转换通过静态方法完成。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Entity
@Table(name = "atomic_component")
public class AtomicComponentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String category = "general";

    @Column(name = "node_type", nullable = false, length = 64)
    private String nodeType;

    @Column(name = "config_template", columnDefinition = "JSON")
    private String configTemplate;

    @Column(name = "input_schema", columnDefinition = "JSON")
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "JSON")
    private String outputSchema;

    @Column(length = 64)
    private String icon;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AtomicComponentEntity() {}

    /**
     * 将领域模型转换为 JPA 实体。
     */
    public static AtomicComponentEntity fromModel(AtomicComponent model) {
        AtomicComponentEntity entity = new AtomicComponentEntity();
        entity.id = model.getId();
        entity.name = model.getName();
        entity.description = model.getDescription();
        entity.category = model.getCategory();
        entity.nodeType = model.getNodeType();
        entity.configTemplate = model.getConfigTemplate();
        entity.inputSchema = model.getInputSchema();
        entity.outputSchema = model.getOutputSchema();
        entity.icon = model.getIcon();
        entity.status = model.getStatus();
        entity.createdBy = model.getCreatedBy();
        entity.createdAt = model.getCreatedAt() != null ? model.getCreatedAt() : LocalDateTime.now();
        entity.updatedAt = model.getUpdatedAt() != null ? model.getUpdatedAt() : LocalDateTime.now();
        return entity;
    }

    /**
     * 将 JPA 实体转换为领域模型。
     */
    public static AtomicComponent toModel(AtomicComponentEntity entity) {
        if (entity == null) return null;
        AtomicComponent model = new AtomicComponent();
        model.setId(entity.id);
        model.setName(entity.name);
        model.setDescription(entity.description);
        model.setCategory(entity.category);
        model.setNodeType(entity.nodeType);
        model.setConfigTemplate(entity.configTemplate);
        model.setInputSchema(entity.inputSchema);
        model.setOutputSchema(entity.outputSchema);
        model.setIcon(entity.icon);
        model.setStatus(entity.status);
        model.setCreatedBy(entity.createdBy);
        model.setCreatedAt(entity.createdAt);
        model.setUpdatedAt(entity.updatedAt);
        return model;
    }

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
}
