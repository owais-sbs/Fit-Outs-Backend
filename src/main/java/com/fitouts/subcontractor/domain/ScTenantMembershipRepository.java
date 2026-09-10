package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScTenantMembershipRepository extends JpaRepository<ScTenantMembership, UUID> {

    Optional<ScTenantMembership> findByOrganizationUuidAndCompanyId(UUID organizationUuid, UUID companyId);

    List<ScTenantMembership> findByCompanyIdOrderByUpdatedAtDesc(UUID companyId);

    Optional<ScTenantMembership> findByCompanyIdAndOrganizationUuid(UUID companyId, UUID organizationUuid);
}
