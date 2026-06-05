package com.fitouts.lead.domain;

import jakarta.persistence.*;
import lombok.*;

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

}