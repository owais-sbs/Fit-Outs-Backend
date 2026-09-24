package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScAwardBoqLineRepository extends JpaRepository<ScAwardBoqLine, UUID> {

    List<ScAwardBoqLine> findByAwardUuidOrderBySortOrderAsc(UUID awardUuid);

    List<ScAwardBoqLine> findByPackageUuidAndCompanyIdOrderBySortOrderAsc(UUID packageUuid, UUID companyId);

    void deleteByAwardUuid(UUID awardUuid);
}
