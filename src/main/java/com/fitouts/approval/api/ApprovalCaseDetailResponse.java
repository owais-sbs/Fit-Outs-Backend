package com.fitouts.approval.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApprovalCaseDetailResponse {

    private ApprovalCaseResponse header;
    private List<CaseChecklistItemResponse> checklist;
    private List<CaseSubmissionResponse> submissions;
    private List<CaseCommentResponse> comments;
    private List<CaseFeeResponse> fees;
    private List<CaseEventResponse> events;
    /** Statuses this case may legally move to right now. */
    private List<String> allowedTransitions;
}
