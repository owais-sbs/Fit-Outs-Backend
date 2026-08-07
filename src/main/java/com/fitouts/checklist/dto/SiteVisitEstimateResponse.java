package com.fitouts.checklist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.checklist.domain.SiteVisitEstimateStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitEstimateResponse {

    private UUID uuid;
    private UUID siteVisitUuid;
    private String quoteNo;
    private LocalDate validUntil;
    private String revision;
    private String clientName;
    private String clientAddress;
    private String projectLabel;
    private String locationLabel;
    private String subject;
    private String preparedBy;
    private String currency;
    private String notes;
    private BigDecimal subtotal;
    private SiteVisitEstimateStatus status;
    @Builder.Default
    private List<SiteVisitEstimateLineResponse> lines = new ArrayList<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
