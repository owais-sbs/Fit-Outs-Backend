package com.fitouts.schedule.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectScheduleRepository extends JpaRepository<ProjectSchedule, UUID> {

    Optional<ProjectSchedule> findByProjectId(Long projectId);
}
