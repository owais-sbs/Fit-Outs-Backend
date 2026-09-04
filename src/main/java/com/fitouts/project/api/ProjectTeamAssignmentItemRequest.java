package com.fitouts.project.api;

import com.fitouts.project.domain.ProjectTeamRole;

import lombok.Data;

@Data
public class ProjectTeamAssignmentItemRequest {

    private ProjectTeamRole role;
    private Long accountId;
}
