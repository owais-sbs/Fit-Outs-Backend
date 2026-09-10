package com.fitouts.subcontractor.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScCompanyProfileRepository extends JpaRepository<ScCompanyProfile, UUID> {

    Optional<ScCompanyProfile> findByAdminAccountIdAndCompanyId(Long adminAccountId, UUID companyId);

    Optional<ScCompanyProfile> findByOrganizationUuidAndCompanyId(UUID organizationUuid, UUID companyId);

    java.util.List<ScCompanyProfile> findByCompanyIdOrderByLegalCompanyNameAsc(UUID companyId);
}
