package com.fitouts.lead.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeadFilterDTO {

    private LeadStatus status;

    private Long sourceId;

    private Long assignedTo;

    private LocalDate startDate;

    private LocalDate endDate;

    private String search;

}