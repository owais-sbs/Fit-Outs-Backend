package com.fitouts.checklist.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitEstimateRequest {

    private String quoteNo;
    private LocalDate validUntil;
    private String revision;
    private String clientName;
    private String clientAddress;
    private String projectLabel;
    private String locationLabel;
    private String subject;
    private String preparedBy;
    private Boolean includeStamp;
    private Boolean includeSignature;
    private String currency;
    private String notes;
    private List<SiteVisitEstimateLineRequest> lines = new ArrayList<>();
    private List<UUID> selectedAppendixIds = new ArrayList<>();
    private List<String> excludedScopeRefs = new ArrayList<>();
}
