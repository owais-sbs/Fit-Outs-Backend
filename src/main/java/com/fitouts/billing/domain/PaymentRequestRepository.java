package com.fitouts.billing.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {

    List<PaymentRequest> findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(UUID milestoneUuid, UUID companyId);

    Optional<PaymentRequest> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<PaymentRequest> findByProjectIdAndCompanyIdAndStatusInOrderByCreatedAtDesc(
            Long projectId, UUID companyId, Collection<BillingStatus> statuses);
}
