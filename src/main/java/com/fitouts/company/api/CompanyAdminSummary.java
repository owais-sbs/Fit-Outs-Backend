package com.fitouts.company.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyAdminSummary {

    private String fullName;
    private String email;
    private String phone;
}
