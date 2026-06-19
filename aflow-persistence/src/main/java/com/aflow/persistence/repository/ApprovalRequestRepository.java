package com.aflow.persistence.repository;

import com.aflow.persistence.entity.ApprovalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ApprovalRequestEntity}.
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, String> {

    List<ApprovalRequestEntity> findByStatus(String status);

    List<ApprovalRequestEntity> findByFlowId(String flowId);

    Optional<ApprovalRequestEntity> findByFlowIdAndNodeId(String flowId, String nodeId);

    List<ApprovalRequestEntity> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);
}
