package com.fitouts.resource.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LabourCrewResponse {
    private UUID uuid;
    private String name;
    private int headcount;
    private boolean active;
}
