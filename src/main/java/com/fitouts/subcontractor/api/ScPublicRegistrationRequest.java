package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScPublicRegistrationRequest {

    private String companyName;
    private String contactName;
    private String email;
    private String phone;
    private String password;
}
