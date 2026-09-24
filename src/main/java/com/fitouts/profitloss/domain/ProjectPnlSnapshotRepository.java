package com.fitouts.profitloss.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectPnlSnapshotRepository extends JpaRepository<ProjectPnlSnapshot, UUID> {

    Optional<ProjectPnlSnapshot> findByCompanyIdAndProjectIdAndPeriodYearMonth(
            UUID companyId, Long projectId, String periodYearMonth);

    Optional<ProjectPnlSnapshot> findFirstByCompanyIdAndProjectIdAndPeriodYearMonthOrderByCalculatedAtDesc(
            UUID companyId, Long projectId, String periodYearMonth);

    Optional<ProjectPnlSnapshot> findFirstByCompanyIdAndProjectIdOrderByCalculatedAtDesc(
            UUID companyId, Long projectId);

    List<ProjectPnlSnapshot> findByCompanyIdAndPeriodYearMonthOrderByProjectIdAsc(
            UUID companyId, String periodYearMonth);
}
