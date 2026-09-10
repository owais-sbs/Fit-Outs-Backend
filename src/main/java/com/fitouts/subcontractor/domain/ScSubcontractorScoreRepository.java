package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScSubcontractorScoreRepository extends JpaRepository<ScSubcontractorScore, UUID> {

    Optional<ScSubcontractorScore> findFirstByOrganizationUuidAndPackageUuidOrderByComputedAtDesc(
            UUID organizationUuid, UUID packageUuid);

    Optional<ScSubcontractorScore> findFirstByOrganizationUuidAndPackageUuidIsNullOrderByComputedAtDesc(
            UUID organizationUuid);

    List<ScSubcontractorScore> findByOrganizationUuidAndCompanyIdOrderByComputedAtDesc(
            UUID organizationUuid, UUID companyId);
}
