package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScSiteReportType;

import lombok.Data;

@Data
public class ScSiteReportRequestBody {
    private Long projectId;
    private UUID packageUuid;
    private ScSiteReportType reportType;
    private String title;
    private String description;
    private String delayReasonCode;
    private LocalDate expectedDate;
    private LocalDate deliveredDate;
    private String materialName;
    private BigDecimal quantity;
    private String unit;
}
