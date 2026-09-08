package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageBidderResponse {

    private UUID id;
    private UUID packageId;
    private UUID subcontractorId;
    private String subcontractorName;
    private String status;
    private OffsetDateTime invitedAt;
    private OffsetDateTime acknowledgedAt;
    private Boolean isEligible;
    private List<String> eligibilityReasons;
}
