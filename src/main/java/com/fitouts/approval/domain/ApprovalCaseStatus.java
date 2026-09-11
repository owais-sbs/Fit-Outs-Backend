package com.fitouts.approval.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Case lifecycle. Transitions are enforced by the service rather than left to the UI, because
 * the whole point of the module is that a pack cannot be submitted with an expired insurance
 * certificate and a permit cannot be submitted before its prerequisite is approved.
 */
public enum ApprovalCaseStatus {

    NOT_STARTED,
    /** At least one required checklist item is still missing or expired. */
    PACK_IN_PREPARATION,
    READY_TO_SUBMIT,
    SUBMITTED,
    UNDER_REVIEW,
    COMMENTS_RECEIVED,
    RESUBMITTED,
    APPROVED,
    ISSUED,
    /** System state, set when the permit is within 30 days of expiry. */
    EXPIRING_SOON,
    RENEWAL_IN_PROGRESS,
    EXPIRED,
    CLOSED,
    REJECTED;

    private static final Set<ApprovalCaseStatus> TERMINAL =
            EnumSet.of(CLOSED, REJECTED);

    /** Statuses at or past approval, used when checking a prerequisite is satisfied. */
    private static final Set<ApprovalCaseStatus> APPROVED_OR_LATER =
            EnumSet.of(APPROVED, ISSUED, EXPIRING_SOON, RENEWAL_IN_PROGRESS, CLOSED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** True once the authority has granted the permit, so dependent cases may proceed. */
    public boolean isApprovedOrLater() {
        return APPROVED_OR_LATER.contains(this);
    }

    /** True while the permit is live and the linked activities are unblocked. */
    public boolean isActivePermit() {
        return this == ISSUED || this == EXPIRING_SOON || this == RENEWAL_IN_PROGRESS;
    }

    /** Whether the SLA clock should be running. */
    public boolean isAwaitingAuthority() {
        return this == SUBMITTED || this == UNDER_REVIEW || this == RESUBMITTED;
    }

    /**
     * The transitions the engine allows. Anything not listed is rejected with the reason,
     * so an invalid move surfaces as a message rather than a silent no-op.
     */
    public Set<ApprovalCaseStatus> allowedNext() {
        return switch (this) {
            case NOT_STARTED -> EnumSet.of(PACK_IN_PREPARATION, READY_TO_SUBMIT);
            case PACK_IN_PREPARATION -> EnumSet.of(READY_TO_SUBMIT);
            case READY_TO_SUBMIT -> EnumSet.of(PACK_IN_PREPARATION, SUBMITTED);
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, COMMENTS_RECEIVED, APPROVED, REJECTED);
            case UNDER_REVIEW -> EnumSet.of(COMMENTS_RECEIVED, APPROVED, REJECTED);
            case COMMENTS_RECEIVED -> EnumSet.of(RESUBMITTED, PACK_IN_PREPARATION, REJECTED);
            case RESUBMITTED -> EnumSet.of(UNDER_REVIEW, COMMENTS_RECEIVED, APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(ISSUED, CLOSED);
            case ISSUED -> EnumSet.of(EXPIRING_SOON, EXPIRED, RENEWAL_IN_PROGRESS, CLOSED);
            case EXPIRING_SOON -> EnumSet.of(RENEWAL_IN_PROGRESS, EXPIRED, CLOSED);
            case RENEWAL_IN_PROGRESS -> EnumSet.of(ISSUED, EXPIRED, CLOSED);
            case EXPIRED -> EnumSet.of(RENEWAL_IN_PROGRESS, CLOSED);
            case CLOSED, REJECTED -> EnumSet.noneOf(ApprovalCaseStatus.class);
        };
    }

    public boolean canMoveTo(ApprovalCaseStatus next) {
        return next != null && allowedNext().contains(next);
    }
}
