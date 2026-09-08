package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageAddendumResponse {

    private UUID id;
    private UUID packageId;
    private Integer addendumNumber;
    private String title;
    private String description;
    private String attachmentPath;
    private OffsetDateTime issuedAt;
    private Long issuedBy;
    private Integer acknowledgedCount;
}
