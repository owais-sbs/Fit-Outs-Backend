package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScCommunityRegistrationRequest {

    private String authorityCode;
    private String authorityName;
    private String registrationNo;
    private String expiryDate;
}
