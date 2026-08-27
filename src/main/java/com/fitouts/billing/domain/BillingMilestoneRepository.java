package com.fitouts.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingMilestoneRepository extends JpaRepository<BillingMilestone, UUID> {

    List<BillingMilestone> findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(Long projectId, UUID companyId);

    Optional<BillingMilestone> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<BillingMilestone> findByLinkedActivityUuidAndCompanyIdAndStatus(
            UUID linkedActivityUuid, UUID companyId, BillingStatus status);
}
