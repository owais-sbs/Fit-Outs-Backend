package com.fitouts.approval.api;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChecklistAttachRequest {

    private String filePath;
    private LocalDate expiryDate;
    private String status;
    /** Director-only override to proceed with a missing or expired document. */
    private String waiverReason;
}
