package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPaymentCertificateRepository extends JpaRepository<ScPaymentCertificate, UUID> {

    Optional<ScPaymentCertificate> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    Optional<ScPaymentCertificate> findByClaimUuid(UUID claimUuid);

    List<ScPaymentCertificate> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID packageUuid, UUID companyId);

    List<ScPaymentCertificate> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(
            Long projectId, UUID companyId);

    List<ScPaymentCertificate> findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
            UUID organizationUuid, UUID companyId);
}
