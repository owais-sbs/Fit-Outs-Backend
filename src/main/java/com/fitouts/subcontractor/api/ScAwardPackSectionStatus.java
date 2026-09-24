package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScAwardPackSectionStatus {
    private String code;
    private String label;
    private String status; // CONNECTED | PARTIAL | NOT_YET_AVAILABLE
    /** Human-readable pack content / source value (not a placeholder status message). */
    private String value;
    private String note;
}
