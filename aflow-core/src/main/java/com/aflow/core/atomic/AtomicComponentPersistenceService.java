package com.aflow.core.atomic;

import com.aflow.common.model.AtomicComponent;

import java.util.List;
import java.util.Optional;

/**
 * 原子能力持久化接口。
 * <p>
 * 定义原子能力组件的 CRUD 操作，由 aflow-persistence 模块实现。
 * 遵循 Interface-in-Core, Impl-in-Persistence 架构模式。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
public interface AtomicComponentPersistenceService {

    /**
     * 保存原子能力（新增或更新）。
     */
    AtomicComponent save(AtomicComponent component);

    /**
     * 根据 ID 查找原子能力。
     */
    Optional<AtomicComponent> findById(String id);

    /**
     * 查询所有原子能力。
     */
    List<AtomicComponent> findAll();

    /**
     * 按分类查询原子能力。
     */
    List<AtomicComponent> findByCategory(String category);

    /**
     * 按状态查询原子能力。
     */
    List<AtomicComponent> findByStatus(String status);

    /**
     * 按名称模糊搜索。
     */
    List<AtomicComponent> searchByName(String keyword);

    /**
     * 删除原子能力。
     */
    void deleteById(String id);
}
