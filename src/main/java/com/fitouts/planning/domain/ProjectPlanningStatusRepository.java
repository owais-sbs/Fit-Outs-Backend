package com.fitouts.planning.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectPlanningStatusRepository extends JpaRepository<ProjectPlanningStatus, Long> {
    Optional<ProjectPlanningStatus> findByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
