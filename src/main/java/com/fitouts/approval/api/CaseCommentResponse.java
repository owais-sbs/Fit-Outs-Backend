package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseCommentResponse {

    private UUID uuid;
    private UUID submissionUuid;
    private String commentText;
    private String reasonCode;
    private LocalDate raisedDate;
    private LocalDate respondedDate;
    private String responseText;
    private String responsibleParty;
}
