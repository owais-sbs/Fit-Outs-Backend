package com.fitouts.schedule.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One precedence relationship between two {@link CpmActivity} codes. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CpmLink {

    private String predecessorCode;
    private String successorCode;
    private DependencyType type = DependencyType.FS;
    private int lagWorkingDays;

    /**
     * A cure, test or strength-gain period. Compression and bar dragging may not reduce it,
     * so the engine treats the lag as a hard minimum rather than a target.
     */
    private boolean locked;
    private String lockReason;

    public static CpmLink fs(String predecessor, String successor) {
        return new CpmLink(predecessor, successor, DependencyType.FS, 0, false, null);
    }
}
