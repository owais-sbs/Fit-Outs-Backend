package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScSubmittalRepository extends JpaRepository<ScSubmittal, UUID> {

    Optional<ScSubmittal> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScSubmittal> findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID organizationUuid, UUID companyId);

    List<ScSubmittal> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
            Long projectId, UUID companyId);
}
