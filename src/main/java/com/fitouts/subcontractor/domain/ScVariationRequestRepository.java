package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScVariationRequestRepository extends JpaRepository<ScVariationRequest, UUID> {

    List<ScVariationRequest> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(UUID packageUuid, UUID companyId);

    Optional<ScVariationRequest> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScVariationRequest> findByCompanyIdAndStatusOrderBySubmittedAtDesc(UUID companyId, ScVariationStatus status);

    List<ScVariationRequest> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);
}
