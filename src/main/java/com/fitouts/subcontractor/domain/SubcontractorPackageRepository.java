package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorPackageRepository extends JpaRepository<SubcontractorPackage, UUID> {

    List<SubcontractorPackage> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<SubcontractorPackage> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<SubcontractorPackage> findByAppointedAccountIdAndCompanyIdOrderByCreatedAtDesc(
            Long appointedAccountId, UUID companyId);

    long countByProjectIdAndCompanyId(Long projectId, UUID companyId);

    long countByProjectIdAndCompanyIdAndStatus(Long projectId, UUID companyId, SubcontractorPackageStatus status);
}
