package com.fitouts.subcontractor.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorRegistrationRequest {

    private String token;
    private String companyName;
    private String licenceNo;
    private LocalDate licenceExpiry;
    private String trn;
    private LocalDate establishmentCardExpiry;
    private String address;
    private String locationPin;
    private List<String> trades;
    private String capacityBand;
}
