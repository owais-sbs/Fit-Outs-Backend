package com.fitouts.roomconfiguration.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.company.domain.Company;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_masters", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = true, length = 50)
    private String code;

    @Column(nullable = true)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = true)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
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
