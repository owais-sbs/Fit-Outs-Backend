package com.fitouts.validation.api;

import java.util.List;

import com.fitouts.subcontractor.api.SubcontractorClaimResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationInboxResponse {
    private List<ProgressValidationResponse> progressItems;
    private List<SubcontractorClaimResponse> claimItems;
    private int pendingProgressCount;
    private int pendingClaimCount;
}
