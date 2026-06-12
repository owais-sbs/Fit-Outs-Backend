package com.fitouts.lead.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;

import com.fitouts.account.domain.Account;
import com.fitouts.company.domain.Company;

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

    @Enumerated(EnumType.STRING)
    private LeadSource source;

    @Column(name = "other_source")
    private String otherSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private Account assignedTo;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company companyEntity;
}
