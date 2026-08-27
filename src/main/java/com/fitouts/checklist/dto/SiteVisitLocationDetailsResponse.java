package com.fitouts.checklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitLocationDetailsResponse {

    private UUID uuid;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String area;
    private String buildingName;
    private String floor;
    private String unitNumber;
    private String landmark;
    private String accessNotes;
    private String mapsShareUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
