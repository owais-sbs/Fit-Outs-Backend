package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPackageClarificationRepository extends JpaRepository<ScPackageClarification, UUID> {

    List<ScPackageClarification> findByPackageUuidOrderByCreatedAtAsc(UUID packageUuid);

    List<ScPackageClarification> findByPackageUuidAndOrganizationUuidOrderByCreatedAtAsc(
            UUID packageUuid, UUID organizationUuid);
}
