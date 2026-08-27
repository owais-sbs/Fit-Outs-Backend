package com.fitouts.holdpoint.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityHoldPointRepository extends JpaRepository<QualityHoldPoint, UUID> {

    List<QualityHoldPoint> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<QualityHoldPoint> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
