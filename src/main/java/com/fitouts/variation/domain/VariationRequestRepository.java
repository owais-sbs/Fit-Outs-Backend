package com.fitouts.variation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VariationRequestRepository extends JpaRepository<VariationRequest, UUID> {
    List<VariationRequest> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<VariationRequest> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<VariationRequest> findByCompanyIdAndStatusOrderByUpdatedAtDesc(UUID companyId, VariationStatus status);

    @Query("SELECT COUNT(v) FROM VariationRequest v WHERE v.projectId = :projectId AND v.companyId = :companyId")
    long countByProject(@Param("projectId") Long projectId, @Param("companyId") UUID companyId);

    List<VariationRequest> findByCompanyIdAndStatusInOrderByUpdatedAtDesc(
            UUID companyId, List<VariationStatus> statuses);
}
