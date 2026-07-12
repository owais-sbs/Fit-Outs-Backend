package com.fitouts.boq.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.BoqApprovalAction;
import com.fitouts.shared.enums.BoqApprovalStep;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boq_approval_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqApprovalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boq_id", nullable = false)
    private BoqDocument boq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BoqApprovalStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BoqApprovalAction action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    private String comments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
