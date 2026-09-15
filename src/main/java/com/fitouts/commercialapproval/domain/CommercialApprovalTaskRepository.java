package com.fitouts.commercialapproval.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.auth.domain.Role;

public interface CommercialApprovalTaskRepository extends JpaRepository<CommercialApprovalTask, UUID> {
    List<CommercialApprovalTask> findByRunUuidOrderByStepOrderAsc(UUID runUuid);

    List<CommercialApprovalTask> findByRunUuidAndStepOrder(UUID runUuid, int stepOrder);

    List<CommercialApprovalTask> findByRunUuidAndStatus(UUID runUuid, CommercialApprovalTaskStatus status);

    @Query("""
            SELECT t FROM CommercialApprovalTask t
            JOIN CommercialApprovalRun r ON r.uuid = t.runUuid
            WHERE r.companyId = :companyId
              AND t.status = com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus.PENDING
              AND t.role IN :roles
            ORDER BY t.dueAt ASC NULLS LAST, t.createdAt ASC
            """)
    List<CommercialApprovalTask> findPendingInbox(
            @Param("companyId") UUID companyId, @Param("roles") List<Role> roles);

    @Query("""
            SELECT t FROM CommercialApprovalTask t
            WHERE t.status = com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus.PENDING
              AND t.dueAt IS NOT NULL AND t.dueAt < :now
            """)
    List<CommercialApprovalTask> findOverduePending(@Param("now") OffsetDateTime now);
}
