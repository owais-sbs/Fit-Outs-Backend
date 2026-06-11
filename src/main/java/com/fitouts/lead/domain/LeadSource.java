package com.fitouts.lead.domain;

import jakarta.persistence.*;
import lombok.*;

import com.fitouts.company.domain.Company;

@Entity
@Table(name = "lead_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceName;

    private boolean isactive;

    private boolean isdeleted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;
}