package com.fitouts.subcontractor.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorWorkerResponse {

    private UUID id;
    private UUID subcontractorId;
    private String name;
    private String trade;
    private String passportNo;
    private LocalDate visaExpiry;
    private LocalDate eidExpiry;
    private LocalDate insuranceExpiry;
    private String certificatesJson;
    private LocalDate inductionDate;
    private String accessCardNo;
    private LocalDate accessCardExpiry;
    private Boolean isSiteEligible;
    private String photoFileId;
}
