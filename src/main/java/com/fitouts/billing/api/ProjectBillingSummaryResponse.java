package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectBillingSummaryResponse {
    private Long projectId;
    private String projectName;
    private BigDecimal billedAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private long milestoneCount;
    private List<BillingMilestoneResponse> milestones;
}
