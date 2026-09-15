package com.fitouts.commercialapproval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialApprovalBandRepository extends JpaRepository<CommercialApprovalBand, UUID> {
    List<CommercialApprovalBand> findByMatrixUuidOrderBySortOrderAscMinAmountAsc(UUID matrixUuid);

    void deleteByMatrixUuid(UUID matrixUuid);
}
