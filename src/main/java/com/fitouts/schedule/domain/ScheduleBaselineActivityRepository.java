package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleBaselineActivityRepository extends JpaRepository<ScheduleBaselineActivity, UUID> {
    List<ScheduleBaselineActivity> findByBaselineUuid(UUID baselineUuid);
}
