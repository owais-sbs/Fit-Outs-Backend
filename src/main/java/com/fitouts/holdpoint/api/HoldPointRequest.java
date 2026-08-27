package com.fitouts.holdpoint.api;

import java.util.List;
import java.util.UUID;

import com.fitouts.holdpoint.domain.HoldPointStatus;

import lombok.Data;

@Data
public class HoldPointRequest {
    private UUID activityUuid;
    private String title;
    private HoldPointStatus status;
    /** Raw JSON array string (legacy). Prefer checklistItems. */
    private String checklistJson;
    /** Preferred: checklist items stored as a JSON array string. */
    private List<String> checklistItems;
    private String activityType;
    private String notes;
}
