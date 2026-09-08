package com.fitouts.schedule.engine;

/**
 * The four precedence relationships. The seed data only uses FS and SS, but the derived trade
 * overlaps and the spec's data model both need the full set.
 */
public enum DependencyType {
    /** Successor starts after predecessor finishes. */
    FS,
    /** Successor starts after predecessor starts. */
    SS,
    /** Successor finishes after predecessor finishes. */
    FF,
    /** Successor finishes after predecessor starts. */
    SF;

    public static DependencyType from(String raw) {
        if (raw == null) return FS;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FS;
        }
    }

    /** True when the relationship drives the successor's start rather than its finish. */
    public boolean drivesSuccessorStart() {
        return this == FS || this == SS;
    }
}
