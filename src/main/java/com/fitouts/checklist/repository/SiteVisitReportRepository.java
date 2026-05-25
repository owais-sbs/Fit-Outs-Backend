package com.fitouts.checklist.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.SiteVisitReport;

public interface SiteVisitReportRepository extends JpaRepository<SiteVisitReport, UUID> {

    boolean existsBySiteVisitUuid(UUID siteVisitUuid);
}
