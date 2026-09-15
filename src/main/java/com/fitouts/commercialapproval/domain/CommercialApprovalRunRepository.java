package com.fitouts.commercialapproval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalRunRepository extends JpaRepository<CommercialApprovalRun, UUID> {
    Optional<CommercialApprovalRun> findFirstByEventTypeAndEntityUuidAndStatusOrderByStartedAtDesc(
            CommercialEventType eventType, UUID entityUuid, CommercialApprovalRunStatus status);

    List<CommercialApprovalRun> findByEventTypeAndEntityUuidOrderByStartedAtDesc(
            CommercialEventType eventType, UUID entityUuid);

    Optional<CommercialApprovalRun> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
