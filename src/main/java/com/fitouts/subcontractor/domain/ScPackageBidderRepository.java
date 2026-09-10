package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPackageBidderRepository extends JpaRepository<ScPackageBidder, UUID> {

    List<ScPackageBidder> findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(UUID packageUuid, UUID companyId);

    Optional<ScPackageBidder> findByPackageUuidAndOrganizationUuid(UUID packageUuid, UUID organizationUuid);

    List<ScPackageBidder> findByOrganizationUuidAndCompanyIdOrderByInvitedAtDesc(
            UUID organizationUuid, UUID companyId);
}
