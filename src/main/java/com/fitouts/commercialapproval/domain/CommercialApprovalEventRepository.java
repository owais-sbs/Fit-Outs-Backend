package com.fitouts.commercialapproval.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalEventRepository extends JpaRepository<CommercialApprovalEvent, UUID> {
    List<CommercialApprovalEvent> findByRunUuidOrderByCreatedAtAsc(UUID runUuid);

    List<CommercialApprovalEvent> findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID companyId, OffsetDateTime from, OffsetDateTime to);
}
