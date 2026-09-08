package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorUserRequest {

    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String permissionsJson;
}
