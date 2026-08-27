package com.fitouts.materialplan.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMaterialPlanLineRepository extends JpaRepository<ProjectMaterialPlanLine, UUID> {
    List<ProjectMaterialPlanLine> findByPlanUuidOrderBySortOrderAsc(UUID planUuid);

    void deleteByPlanUuid(UUID planUuid);
}
