package com.fitouts.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingMilestoneRepository extends JpaRepository<BillingMilestone, UUID> {

    List<BillingMilestone> findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(Long projectId, UUID companyId);

    Optional<BillingMilestone> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<BillingMilestone> findByLinkedActivityUuidAndCompanyIdAndStatus(
            UUID linkedActivityUuid, UUID companyId, BillingStatus status);

    List<BillingMilestone> findByCompanyIdOrderByDueDateAscCreatedAtAsc(UUID companyId);

    @Query("""
            SELECT m.projectId, m.status, COALESCE(SUM(m.amount), 0), COUNT(m)
            FROM BillingMilestone m
            WHERE m.companyId = :companyId
            GROUP BY m.projectId, m.status
            """)
    List<Object[]> aggregateAmountByProjectAndStatus(@Param("companyId") UUID companyId);
}
