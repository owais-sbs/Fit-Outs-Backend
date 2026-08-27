package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleActivityRepository extends JpaRepository<ScheduleActivity, UUID> {
    List<ScheduleActivity> findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(Long projectId, UUID companyId);

    Optional<ScheduleActivity> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScheduleActivity> findByAssigneeAccountIdAndCompanyIdOrderByStartDateAsc(Long assigneeAccountId, UUID companyId);

    List<ScheduleActivity> findByProjectIdAndCompanyIdAndPublishStatus(
            Long projectId, UUID companyId, SchedulePublishStatus publishStatus);
}
