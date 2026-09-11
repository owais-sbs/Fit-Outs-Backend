package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScSubcontractContractResponse {
    private UUID awardUuid;
    private UUID packageUuid;
    private String packageName;
    private UUID organizationUuid;
    private String organizationName;
    private BigDecimal awardedValue;
    private OffsetDateTime awardedAt;
    private String contractStatus;
    private String contractFilePath;
    private boolean contractAvailable;
    private boolean adminSigned;
    private OffsetDateTime adminSignedAt;
    private String adminSignerName;
    private String adminSignerTitle;
    private String adminSignatureAuditJson;
    private boolean subcontractorSignatureUploaded;
    private String subcontractorSignatureUrl;
    private boolean signed;
    private OffsetDateTime signedAt;
    private String subcontractorSignerName;
    private String subcontractorSignerTitle;
    private String signatureAuditJson;
}
