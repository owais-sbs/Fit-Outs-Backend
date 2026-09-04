package com.fitouts.project.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectQasSurveySeedResponse {

    private Long projectId;
    private UUID sourceEstimateUuid;
    private List<Object> floors;
    private List<Object> rooms;
}
