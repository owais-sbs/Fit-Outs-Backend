package com.fitouts.materialplan.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_material_plan_package")
@Getter
@Setter
public class ProjectMaterialPlanPackage {

    @Id
    private UUID uuid;

    @Column(name = "plan_uuid", nullable = false)
    private UUID planUuid;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
