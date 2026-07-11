package com.fitouts.workitemconfiguration.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemMaterialRepository extends JpaRepository<WorkItemMaterial, UUID> {
    List<WorkItemMaterial> findByWorkItemId(UUID workItemId);
    void deleteByWorkItemId(UUID workItemId);
}
