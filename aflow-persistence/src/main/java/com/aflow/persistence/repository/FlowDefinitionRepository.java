package com.aflow.persistence.repository;

import com.aflow.persistence.entity.FlowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for flow definitions.
 */
@Repository
public interface FlowDefinitionRepository extends JpaRepository<FlowDefinitionEntity, String> {

    /**
     * Find all flow definitions with the given status.
     *
     * @param status the status to filter by (DRAFT, PUBLISHED, ARCHIVED)
     * @return list of matching definitions
     */
    List<FlowDefinitionEntity> findByStatus(String status);
}
