package com.fitouts.materialplan.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMaterialPlanRepository extends JpaRepository<ProjectMaterialPlan, UUID> {
    Optional<ProjectMaterialPlan> findByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
