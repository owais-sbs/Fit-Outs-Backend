package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseCommentRequest {

    private UUID submissionUuid;
    private String commentText;
    private String reasonCode;
    private LocalDate raisedDate;
    private String responseText;
    private LocalDate respondedDate;
    private String responsibleParty;
}
