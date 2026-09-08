package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageClarificationRequest {

    private String question;
    private String answer;
    private UUID subcontractorId; // Optional private targeting
}
