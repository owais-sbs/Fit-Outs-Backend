package com.fitouts.subcontractor.api;

import java.util.UUID;

import com.fitouts.subcontractor.domain.ScAttendanceParty;

import lombok.Data;

@Data
public class ScAttendanceRequest {
    private UUID uuid;
    private String responsibilityType;
    private ScAttendanceParty responsibleParty;
    private String notes;
}
