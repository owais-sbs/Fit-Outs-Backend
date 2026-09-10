package com.fitouts.subcontractor.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScCompanyProfileRequest {

    private String legalCompanyName;
    private String tradeLicenceNumber;
    private String tradeLicenceExpiry;
    private String trn;
    private String registeredAddress;
    private String primaryContactName;
    private String primaryContactEmail;
    private String primaryContactPhone;
    private String accountsContactName;
    private String accountsContactEmail;
    private String accountsContactPhone;
    private List<String> tradeCategories;
    private String declaredCapacity;
    private String status;
}
