package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScCommunityRegistrationRepository extends JpaRepository<ScCommunityRegistration, UUID> {

    List<ScCommunityRegistration> findByOrganizationUuidOrderByAuthorityNameAsc(UUID organizationUuid);
}
