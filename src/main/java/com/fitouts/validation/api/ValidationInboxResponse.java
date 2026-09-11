package com.fitouts.validation.api;

import java.util.List;

import com.fitouts.subcontractor.api.SubcontractorClaimResponse;
import com.fitouts.subcontractor.api.ScInvoiceResponse;
import com.fitouts.subcontractor.api.ScVariationResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationInboxResponse {
    private List<ProgressValidationResponse> progressItems;
    private List<SubcontractorClaimResponse> claimItems;
    private List<ScVariationResponse> variationItems;
    private List<ScInvoiceResponse> invoiceItems;
    private int pendingProgressCount;
    private int pendingClaimCount;
    private int pendingVariationCount;
    private int pendingInvoiceCount;
}
