package com.aflow.persistence.repository;

import com.aflow.persistence.entity.FlowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for flow instances.
 */
@Repository
public interface FlowInstanceRepository extends JpaRepository<FlowInstanceEntity, String> {

    /**
     * Find all flow instances for a given definition.
     *
     * @param definitionId the flow definition ID
     * @return list of matching instances
     */
    List<FlowInstanceEntity> findByDefinitionId(String definitionId);

    /**
     * Find all flow instances with the given status.
     *
     * @param status the status to filter by
     * @return list of matching instances
     */
    List<FlowInstanceEntity> findByStatus(String status);
}
