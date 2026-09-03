package com.fitouts.schedule.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleActivityRepository extends JpaRepository<ScheduleActivity, UUID> {
    List<ScheduleActivity> findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(Long projectId, UUID companyId);

    Optional<ScheduleActivity> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScheduleActivity> findByAssigneeAccountIdAndCompanyIdOrderByStartDateAsc(Long assigneeAccountId, UUID companyId);

    List<ScheduleActivity> findByProjectIdAndCompanyIdAndPublishStatus(
            Long projectId, UUID companyId, SchedulePublishStatus publishStatus);

    @Query("""
            SELECT a FROM ScheduleActivity a
            WHERE a.companyId = :companyId
              AND a.publishStatus = com.fitouts.schedule.domain.SchedulePublishStatus.PUBLISHED
              AND a.startDate <= :endDate AND a.endDate >= :startDate
              AND (:projectId IS NULL OR a.projectId = :projectId)
              AND (:assigneeAccountId IS NULL OR a.assigneeAccountId = :assigneeAccountId)
            ORDER BY a.startDate ASC, a.sortOrder ASC
            """)
    List<ScheduleActivity> findPublishedInDateRange(
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("projectId") Long projectId,
            @Param("assigneeAccountId") Long assigneeAccountId);
}
