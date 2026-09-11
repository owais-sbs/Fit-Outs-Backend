package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ProgressUpdateRequest {
    private Integer percentComplete;
    private String notes;
    private BigDecimal labourHours;
    private String delayReason;
    /** Optional materials used on this progress update. */
    private List<MaterialIssueRequest> materialIssues;
}
