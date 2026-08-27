package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleBaselineDetailResponse {
    private UUID uuid;
    private String name;
    private OffsetDateTime createdAt;
    private Long createdBy;
    private List<BaselineActivitySnap> activities;

    @Data
    @Builder
    public static class BaselineActivitySnap {
        private UUID activityUuid;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private int percentComplete;
        private BigDecimal weight;
    }
}
