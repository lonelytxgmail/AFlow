package com.aflow.persistence.atomic;

import com.aflow.common.model.AtomicComponent;
import com.aflow.core.atomic.AtomicComponentPersistenceService;
import com.aflow.persistence.entity.AtomicComponentEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 原子能力 JPA 持久化实现。
 * <p>
 * 使用 Spring Data JPA + EntityManager 实现 {@link AtomicComponentPersistenceService}。
 * 遵循 Interface-in-Core, Impl-in-Persistence 架构模式。
 *
 * @author AFlow Team
 * @since 1.0.0
 */
@Repository
@Transactional
public class JpaAtomicComponentPersistenceService implements AtomicComponentPersistenceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public AtomicComponent save(AtomicComponent component) {
        AtomicComponentEntity entity = AtomicComponentEntity.fromModel(component);
        if (entity.getId() == null) {
            entity.setId(java.util.UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        entityManager.merge(entity);
        return AtomicComponentEntity.toModel(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AtomicComponent> findById(String id) {
        AtomicComponentEntity entity = entityManager.find(AtomicComponentEntity.class, id);
        return Optional.ofNullable(entity).map(AtomicComponentEntity::toModel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtomicComponent> findAll() {
        return entityManager.createQuery("SELECT a FROM AtomicComponentEntity a ORDER BY a.updatedAt DESC", AtomicComponentEntity.class)
                .getResultList().stream()
                .map(AtomicComponentEntity::toModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtomicComponent> findByCategory(String category) {
        return entityManager.createQuery("SELECT a FROM AtomicComponentEntity a WHERE a.category = :cat ORDER BY a.updatedAt DESC", AtomicComponentEntity.class)
                .setParameter("cat", category)
                .getResultList().stream()
                .map(AtomicComponentEntity::toModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtomicComponent> findByStatus(String status) {
        return entityManager.createQuery("SELECT a FROM AtomicComponentEntity a WHERE a.status = :status ORDER BY a.updatedAt DESC", AtomicComponentEntity.class)
                .setParameter("status", status)
                .getResultList().stream()
                .map(AtomicComponentEntity::toModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtomicComponent> searchByName(String keyword) {
        return entityManager.createQuery("SELECT a FROM AtomicComponentEntity a WHERE a.name LIKE :kw ORDER BY a.updatedAt DESC", AtomicComponentEntity.class)
                .setParameter("kw", "%" + keyword + "%")
                .getResultList().stream()
                .map(AtomicComponentEntity::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        AtomicComponentEntity entity = entityManager.find(AtomicComponentEntity.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }
}
