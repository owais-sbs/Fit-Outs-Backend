package com.fitouts.company.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CoverLetterBrandingResponse {

    private String stampUrl;
    private String signatureUrl;
}
