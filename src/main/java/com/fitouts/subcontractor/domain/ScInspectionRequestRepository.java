package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScInspectionRequestRepository extends JpaRepository<ScInspectionRequest, UUID> {

    List<ScInspectionRequest> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    List<ScInspectionRequest> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(UUID packageUuid, UUID companyId);

    List<ScInspectionRequest> findByPackageUuidAndOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID packageUuid, UUID organizationUuid, UUID companyId);

    Optional<ScInspectionRequest> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    Optional<ScInspectionRequest> findByUuidAndProjectIdAndCompanyId(UUID uuid, Long projectId, UUID companyId);
}
