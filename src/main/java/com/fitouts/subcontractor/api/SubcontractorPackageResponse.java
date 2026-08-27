package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubcontractorPackageResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String name;
    private String boqSectionCode;
    private SubcontractorPackageStatus status;
    private Long appointedAccountId;
    private String appointedCompanyName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
