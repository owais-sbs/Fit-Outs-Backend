package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScFreeIssueMaterial;
import com.fitouts.subcontractor.domain.ScPackageAttendance;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScAwardPackResponse {
    private UUID uuid;
    private UUID packageUuid;
    private String packageName;
    private Long projectId;
    private String projectName;
    private String projectLocation;
    private UUID organizationUuid;
    private String organizationName;
    private UUID quoteUuid;
    private Integer quoteVersion;
    private BigDecimal quotedValue;
    private BigDecimal awardedValue;
    private BigDecimal originalAwardValue;
    private String awardValueReason;
    private OffsetDateTime awardedAt;
    private String packageStatus;
    private String tenderStatus;
    private String contractStatus;
    private String contractFilePath;
    private OffsetDateTime adminSignedAt;
    private String adminSignerName;
    private String adminSignerTitle;
    private OffsetDateTime signedAt;
    private String subcontractorSignerName;
    private String subcontractorSignerTitle;
    private String signatureAuditJson;

    private String tradePackageCode;
    private String tradePackageName;
    private String paymentTerms;
    private BigDecimal retentionPct;
    private String ldTerms;
    private LocalDate plannedStart;
    private LocalDate plannedFinish;
    private String activityCodes;
    private String tenderDescription;
    private String exclusionsText;
    private String qualificationsText;
    private String specialistLicenceRequired;

    private List<ScAwardBoqLineResponse> awardedBoqLines;
    private List<ScFreeIssueMaterial> freeIssueMaterials;
    private List<ScPackageAttendance> attendanceMatrix;
    private List<ScAwardPackSectionStatus> sections;
    private List<String> closeoutGaps;
}
