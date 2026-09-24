package com.fitouts.completion.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.completion.domain.CommercialLifecycleStage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloseoutChecklistResponse {

    private Long projectId;
    private boolean allSatisfied;
    private int satisfiedCount;
    private int outstandingCount;
    private String summary;
    private List<Item> items;

    private CommercialLifecycleStage commercialStage;
    private boolean archiveEligible;
    private OffsetDateTime commerciallyClosedAt;
    private Long commerciallyClosedBy;
    private LocalDate dlpStartDate;
    private LocalDate dlpEndDate;
    private Integer dlpDurationMonths;
    private OffsetDateTime archivedAt;
    private Long archivedBy;

    @Data
    @Builder
    public static class Item {
        private String key;
        private String title;
        private String evaluation;
        private boolean satisfied;
        private String detail;
        private int outstandingCount;
        private int totalCount;
        private int carriedForwardCount;
        private OffsetDateTime confirmedAt;
        private Long confirmedBy;
        private List<Entry> entries;
    }

    @Data
    @Builder
    public static class Entry {
        private UUID uuid;
        private String reference;
        private String title;
        private String status;
        private boolean carriedForward;
    }
}
