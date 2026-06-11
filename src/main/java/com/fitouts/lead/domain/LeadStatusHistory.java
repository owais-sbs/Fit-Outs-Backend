package com.fitouts.lead.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lead_status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leadId;

    @Column(name = "company_id")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    private LeadStatus status;

    private Long updatedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String lostReason;

    private LocalDateTime createdAt;
}