package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorClaimRepository extends JpaRepository<SubcontractorClaim, UUID> {

    List<SubcontractorClaim> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(UUID packageUuid, UUID companyId);

    Optional<SubcontractorClaim> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<SubcontractorClaim> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    List<SubcontractorClaim> findByCompanyIdAndStatusOrderBySubmittedAtDesc(
            UUID companyId, SubcontractorClaimStatus status);
}
