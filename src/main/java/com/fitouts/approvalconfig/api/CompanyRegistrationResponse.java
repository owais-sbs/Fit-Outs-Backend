package com.fitouts.approvalconfig.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CompanyRegistrationResponse {
    private UUID id;
    private UUID authorityId;
    private String authorityCode;
    private String authorityName;
    private String referenceNo;
    private LocalDate registrationDate;
    private LocalDate renewalDate;
    private String status;
    private String notes;
    private boolean active;
    private LocalDateTime updatedAt;
}
