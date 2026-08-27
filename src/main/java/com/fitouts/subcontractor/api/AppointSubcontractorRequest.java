package com.fitouts.subcontractor.api;

import lombok.Data;

@Data
public class AppointSubcontractorRequest {
    private Long accountId;
    private String companyName;
}
