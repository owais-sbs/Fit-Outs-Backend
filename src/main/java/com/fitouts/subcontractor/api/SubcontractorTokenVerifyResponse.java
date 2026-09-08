package com.fitouts.subcontractor.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubcontractorTokenVerifyResponse {

    private boolean valid;
    private String email;
    private String companyName;
    private String message;
}
