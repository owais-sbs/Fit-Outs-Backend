package com.fitouts.materialplan.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMaterialPlanPackageRepository extends JpaRepository<ProjectMaterialPlanPackage, UUID> {
    List<ProjectMaterialPlanPackage> findByPlanUuidOrderBySortOrderAsc(UUID planUuid);

    void deleteByPlanUuid(UUID planUuid);
}
