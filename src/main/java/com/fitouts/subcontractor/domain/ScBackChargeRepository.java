package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScBackChargeRepository extends JpaRepository<ScBackCharge, UUID> {

    Optional<ScBackCharge> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScBackCharge> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID packageUuid, UUID companyId);

    List<ScBackCharge> findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID organizationUuid, UUID companyId);

    List<ScBackCharge> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
            Long projectId, UUID companyId);

    List<ScBackCharge> findByPackageUuidAndOrganizationUuidAndStatusIn(
            UUID packageUuid, UUID organizationUuid, List<ScBackChargeStatus> statuses);
}
