package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScClaimLineRepository extends JpaRepository<ScClaimLine, UUID> {

    List<ScClaimLine> findByClaimUuidOrderBySortOrderAsc(UUID claimUuid);

    List<ScClaimLine> findByAwardBoqLineUuidAndCompanyId(UUID awardBoqLineUuid, UUID companyId);

    void deleteByClaimUuid(UUID claimUuid);
}
