package com.fitouts.commercialapproval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalMatrixRepository extends JpaRepository<CommercialApprovalMatrix, UUID> {
    List<CommercialApprovalMatrix> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<CommercialApprovalMatrix> findFirstByCompanyIdAndEventTypeAndActiveTrueOrderByCreatedAtDesc(
            UUID companyId, CommercialEventType eventType);

    Optional<CommercialApprovalMatrix> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
