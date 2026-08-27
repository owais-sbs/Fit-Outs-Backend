package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleBaselineRepository extends JpaRepository<ScheduleBaseline, UUID> {
    List<ScheduleBaseline> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);
}
