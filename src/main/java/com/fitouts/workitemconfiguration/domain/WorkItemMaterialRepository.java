package com.fitouts.workitemconfiguration.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemMaterialRepository extends JpaRepository<WorkItemMaterial, UUID> {
    List<WorkItemMaterial> findByWorkItemId(UUID workItemId);

    @Query("SELECT wim FROM WorkItemMaterial wim JOIN FETCH wim.material m LEFT JOIN FETCH m.materialCategory WHERE wim.workItem.id IN :workItemIds")
    List<WorkItemMaterial> findByWorkItemIdIn(@Param("workItemIds") List<UUID> workItemIds);

    void deleteByWorkItemId(UUID workItemId);
}
