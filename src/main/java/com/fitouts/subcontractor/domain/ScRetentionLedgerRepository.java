package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScRetentionLedgerRepository extends JpaRepository<ScRetentionLedger, UUID> {

    List<ScRetentionLedger> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID packageUuid, UUID companyId);

    List<ScRetentionLedger> findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID organizationUuid, UUID companyId);

    List<ScRetentionLedger> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
            Long projectId, UUID companyId);
}
