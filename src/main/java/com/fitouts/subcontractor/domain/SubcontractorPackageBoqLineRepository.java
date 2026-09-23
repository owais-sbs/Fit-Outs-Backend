package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorPackageBoqLineRepository
        extends JpaRepository<SubcontractorPackageBoqLine, UUID> {

    List<SubcontractorPackageBoqLine> findByPackageUuidAndCompanyIdOrderBySortOrderAsc(
            UUID packageUuid, UUID companyId);

    List<SubcontractorPackageBoqLine> findByPackageUuidInAndCompanyId(
            List<UUID> packageUuids, UUID companyId);

    List<SubcontractorPackageBoqLine> findByCompanyIdAndBoqLineId(UUID companyId, UUID boqLineId);

    void deleteByPackageUuidAndCompanyId(UUID packageUuid, UUID companyId);
}
