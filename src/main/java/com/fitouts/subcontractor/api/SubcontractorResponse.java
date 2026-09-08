package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorResponse {

    private UUID id;
    private UUID companyId;
    private String companyName;
    private String licenceNo;
    private LocalDate licenceExpiry;
    private String trn;
    private LocalDate establishmentCardExpiry;
    private String address;
    private String locationPin;
    private String trades;
    private String capacityBand;
    private SubcontractorStatus status;
    private String approvedTrades;
    private BigDecimal maxPackageValue;
    private BigDecimal performanceScore;
    private Boolean isCrossTenantVisible;
    private OffsetDateTime createdAt;
}
