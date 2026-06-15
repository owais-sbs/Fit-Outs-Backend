package com.fitouts.roomconfiguration.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fitouts.company.domain.Company;
import com.fitouts.shared.enums.RoomCategory;
import com.fitouts.workitemconfiguration.domain.WorkItem;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_types", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "room_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "room_type_name", nullable = false, length = 200)
    private String roomTypeName;

    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RoomCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "ceiling_measurement_required", nullable = false)
    @Builder.Default
    private Boolean ceilingMeasurementRequired = false;

    @Column(name = "wall_measurement_required", nullable = false)
    @Builder.Default
    private Boolean wallMeasurementRequired = false;

    @Column(name = "floor_measurement_required", nullable = false)
    @Builder.Default
    private Boolean floorMeasurementRequired = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "room_type_work_items",
        joinColumns = @JoinColumn(name = "room_type_id"),
        inverseJoinColumns = @JoinColumn(name = "work_item_id")
    )
    @Builder.Default
    private Set<WorkItem> workItems = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
