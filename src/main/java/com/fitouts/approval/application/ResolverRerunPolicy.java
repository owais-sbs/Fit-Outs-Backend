package com.fitouts.approval.application;

import com.fitouts.approval.domain.ApprovalCaseStatus;

/** Re-run rules: add missing cases, never duplicate a live permit+authority pair. */
public final class ResolverRerunPolicy {

    private ResolverRerunPolicy() {
    }

    /** A live (non-terminal) case for this permit + authority blocks creating another. */
    public static boolean blocksDuplicate(ApprovalCaseStatus status) {
        return status != null && !status.isTerminal();
    }
}
