package com.fitouts.project.api;

import java.util.UUID;

import com.fitouts.project.domain.ProjectTeamRole;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectTeamAssignmentResponse {

    private UUID uuid;
    private Long projectId;
    private Long accountId;
    private ProjectTeamRole role;
    private String roleLabel;
    private String displayName;
    private String email;
}
