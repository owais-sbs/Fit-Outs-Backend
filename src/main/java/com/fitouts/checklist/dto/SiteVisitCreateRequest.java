package com.fitouts.checklist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitCreateRequest {

    @NotNull
    private Long leadId;

    @NotNull
    private Long assignedTo;

    @NotNull
    private LocalDate scheduledDate;

    @NotNull
    private LocalTime scheduledTime;

    @NotNull
    @DecimalMin("-90.00000000")
    @DecimalMax("90.00000000")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.00000000")
    @DecimalMax("180.00000000")
    private BigDecimal longitude;

    @NotNull
    private UUID checklistTemplateUuid;

    private String notes;
    private Long createdBy;

    public UUID getChecklistTemplateUuid() {
        return checklistTemplateUuid;
    }
}
