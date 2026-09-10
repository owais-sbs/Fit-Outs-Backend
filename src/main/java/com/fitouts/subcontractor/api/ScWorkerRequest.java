package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScWorkerRequest {

    private String fullName;
    private String trade;
    private String passportNumber;
    private String visaExpiry;
    private String emiratesIdExpiry;
    private String insuranceExpiry;
    private String inductionDate;
    private String accessCardExpiry;
    private String tradeCertExpiry;
    private String accessCardNumber;
    private Boolean inductionCompleted;
    private Boolean active;
}
