package com.fitouts.subcontractor.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScUpdateTeamMemberRequest {

    private String portalRole;
    private String status;
    private List<String> permissions;
}
