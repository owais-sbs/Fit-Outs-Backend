package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPortalUserRepository extends JpaRepository<ScPortalUser, UUID> {

    Optional<ScPortalUser> findByAccountId(Long accountId);

    List<ScPortalUser> findByOrganizationUuidOrderByCreatedAtAsc(UUID organizationUuid);
}
