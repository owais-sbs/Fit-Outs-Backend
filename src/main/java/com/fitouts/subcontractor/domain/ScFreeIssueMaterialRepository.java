package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScFreeIssueMaterialRepository extends JpaRepository<ScFreeIssueMaterial, UUID> {
    List<ScFreeIssueMaterial> findByPackageUuidAndCompanyIdOrderBySortOrderAsc(
            UUID packageUuid, UUID companyId);
    void deleteByPackageUuidAndCompanyId(UUID packageUuid, UUID companyId);
}
