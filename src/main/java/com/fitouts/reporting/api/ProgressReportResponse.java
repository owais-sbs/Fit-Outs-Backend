package com.fitouts.reporting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProgressReportResponse {
    private Long projectId;
    private BigDecimal weightedCompletionPercent;
    private List<ActivityProgressRow> activities;
    private List<String> delayReasonCodes;
    private String summary;
    private String baselineName;
    private UUID baselineUuid;

    @Data
    @Builder
    public static class ActivityProgressRow {
        private UUID activityUuid;
        private String name;
        private int percent;
        private BigDecimal weight;
        private LocalDate start;
        private LocalDate end;
        private LocalDate baselineStart;
        private LocalDate baselineEnd;
        private String delayReason;
    }
}
