package com.fitouts.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitLocationDetailsRequest {

    @NotBlank
    private String addressLine1;

    private String addressLine2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    private String country;

    @NotBlank
    private String pincode;

    private String area;
    private String buildingName;
    private String floor;
    private String unitNumber;
    private String landmark;
    private String accessNotes;
    private String mapsShareUrl;
}
