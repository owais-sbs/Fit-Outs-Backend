package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseChecklistItemResponse {

    private UUID uuid;
    private String documentTypeCode;
    private String documentTypeName;
    private String category;
    private boolean required;
    private String status;
    private String filePath;
    /** AUTO_COLLECTED when pack assembly found it, UPLOADED when a person attached it. */
    private String source;
    private LocalDate expiryDate;
    private Long daysToExpiry;
    private Long waivedBy;
    private String waiverReason;
    private boolean blocking;
}
