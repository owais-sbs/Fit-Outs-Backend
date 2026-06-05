package com.fitouts.lead.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "leads")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String referenceNo;

    @Column(name = "client_name")
    private String clientName;

    private String phone;

    private String email;

    @Column(name = "project_type")
    private String projectType;

    private String company;

    private BigDecimal budget;

    private String priority;

    private String location;

    @Column(name = "expected_start_date")
    private LocalDate expectedStartDate;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    private LeadStatus status;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate;

    private boolean isactive;

    private boolean isdeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
