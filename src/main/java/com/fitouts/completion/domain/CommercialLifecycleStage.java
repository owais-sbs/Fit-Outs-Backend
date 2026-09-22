package com.fitouts.completion.domain;

/**
 * Module 27 commercial lifecycle stages. Independent of {@code Project.status}.
 * {@link #ARCHIVE_ELIGIBLE} is a display/filter helper, not a persisted phase.
 */
public enum CommercialLifecycleStage {
    NOT_READY,
    READY_FOR_COMMERCIAL_CLOSE,
    DEFECTS_LIABILITY,
    ARCHIVE_ELIGIBLE,
    ARCHIVED
}
