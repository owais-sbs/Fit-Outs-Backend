package com.fitouts.subcontractor.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScSiteReportRepository extends JpaRepository<ScSiteReport, UUID> {

    Optional<ScSiteReport> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScSiteReport> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    List<ScSiteReport> findByCompanyIdAndReportTypeOrderByCreatedAtDesc(UUID companyId, ScSiteReportType reportType);

    List<ScSiteReport> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<ScSiteReport> findByCompanyIdAndStatusInOrderByCreatedAtDesc(
            UUID companyId, Collection<ScSiteReportStatus> statuses);

    List<ScSiteReport> findByProjectIdAndCompanyIdAndStatusInOrderByCreatedAtDesc(
            Long projectId, UUID companyId, Collection<ScSiteReportStatus> statuses);
}
