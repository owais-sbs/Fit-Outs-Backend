package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthorityResponse {
    private UUID id;
    private String code;
    private String name;
    private String type;
    private String emirate;
    private String jurisdictionAreas;
    private String permitsIssued;
    private String submissionChannel;
    private String notes;
    private boolean requiresCompanyRegistration;
    private boolean appliesEmirateWide;
    private boolean active;
    private LocalDateTime updatedAt;
}
