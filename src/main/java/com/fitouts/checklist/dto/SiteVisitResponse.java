package com.fitouts.checklist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.checklist.domain.SiteVisitPropertyType;
import com.fitouts.checklist.domain.SiteVisitStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitResponse {

    private UUID uuid;
    private Long leadId;
    private List<Long> employeeIds;
    private List<String> employeeNames;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private SiteVisitStatus status;
    private String notes;
    private Long createdBy;
    private UUID checklistTemplateUuid;
    private SiteVisitPropertyType propertyType;
    private String propertyTypeCustom;
    @Builder.Default
    private List<RoomScopeDto> roomScopes = new ArrayList<>();
    @Builder.Default
    private List<String> categories = new ArrayList<>();
    @Builder.Default
    private List<String> rooms = new ArrayList<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private SiteVisitLocationDetailsResponse locationDetails;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
