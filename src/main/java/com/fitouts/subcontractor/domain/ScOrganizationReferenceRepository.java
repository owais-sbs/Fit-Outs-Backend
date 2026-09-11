package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScOrganizationReferenceRepository extends JpaRepository<ScOrganizationReference, UUID> {

    List<ScOrganizationReference> findByOrganizationUuidOrderByYearCompletedDesc(UUID organizationUuid);

    Optional<ScOrganizationReference> findByUuidAndOrganizationUuid(UUID uuid, UUID organizationUuid);
}
