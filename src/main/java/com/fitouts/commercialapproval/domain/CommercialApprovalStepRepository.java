package com.fitouts.commercialapproval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalStepRepository extends JpaRepository<CommercialApprovalStep, UUID> {
    List<CommercialApprovalStep> findByBandUuidOrderByStepOrderAsc(UUID bandUuid);

    void deleteByBandUuid(UUID bandUuid);
}
