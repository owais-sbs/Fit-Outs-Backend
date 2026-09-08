package com.fitouts.subcontractor.api;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorWorkerRequest {

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
    private String photoFileId;
}
