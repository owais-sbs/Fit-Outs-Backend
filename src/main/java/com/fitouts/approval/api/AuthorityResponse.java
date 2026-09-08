package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthorityResponse {

    private UUID uuid;
    private String code;
    private String name;
    private String type;
    private String emirate;
    private String jurisdictionAreas;
    private String permitsIssuedTypical;
    private String submissionChannel;
    private String portalUrl;
    private String notes;
    private boolean active;
    private String verifiedBy;
    private LocalDate verifiedDate;
    private String sourceUrl;
    /** False for global seed rows, true for rows this tenant added. */
    private boolean tenantOwned;
}
