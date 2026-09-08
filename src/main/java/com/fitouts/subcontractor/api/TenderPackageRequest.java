package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenderPackageRequest {

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

    private Boolean requireCarInsurance;
    private Boolean requireTplInsurance;
    private Boolean requireWorkmenComp;
    private Boolean requireCivilDefence;
    private Boolean requireSiraLicence;
    private Boolean requireElectricalCert;
    private String requiredCommunityReg;
}
