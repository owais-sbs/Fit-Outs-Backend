package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/** Shared BOQ scope row for side-by-side comparison columns. */
@Data
@Builder
public class ScComparisonScopeLine {
    private UUID boqLineId;
    private String description;
    private String sectionCode;
    private BigDecimal quantity;
    private String unit;
    private boolean preliminaries;
    private boolean provisionalSum;
}
