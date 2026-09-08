package com.fitouts.subcontractor.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorEligibilityResponse {

    private UUID subcontractorId;
    private Boolean eligible;
    private SubcontractorStatus status;
    private Boolean licenceExpired;
    private Long totalWorkers;
    private Long ineligibleWorkersCount;
    private List<String> ineligibilityReasons = new ArrayList<>();
}
