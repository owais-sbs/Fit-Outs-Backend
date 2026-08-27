package com.fitouts.resource.api;

import lombok.Data;

@Data
public class LabourCrewRequest {
    private String name;
    private Integer headcount;
    private Boolean active;
}
