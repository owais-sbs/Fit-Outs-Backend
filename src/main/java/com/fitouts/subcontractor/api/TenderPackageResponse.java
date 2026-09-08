package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.PackageStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenderPackageResponse {

    private UUID id;
    private Long projectId;
    private UUID companyId;
    private String packageRef;
    private String title;
    private String scope;
    private UUID boqDocumentId;
    private List<UUID> boqLineIds;
    private String trade;
    private String drawingRevision;
    private String specificationSummary;
    private String siteVisitInfo;
    private OffsetDateTime tenderDeadline;
    private PackageStatus status;
    private Boolean bidsOpened;
    private OffsetDateTime bidsOpenedAt;
    private Long bidsOpenedBy;

    private Boolean requireCarInsurance;
    private Boolean requireTplInsurance;
    private Boolean requireWorkmenComp;
    private Boolean requireCivilDefence;
    private Boolean requireSiraLicence;
    private Boolean requireElectricalCert;
    private String requiredCommunityReg;

    private OffsetDateTime createdAt;
    private Integer bidderCount;
}
