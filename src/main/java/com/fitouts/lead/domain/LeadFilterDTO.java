package com.fitouts.lead.domain;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class LeadFilterDTO {

    private LeadStatus status;

    private Long assignedTo;

    private LocalDate startDate;

    private LocalDate endDate;

    private String search;

    private UUID companyUuid;
}