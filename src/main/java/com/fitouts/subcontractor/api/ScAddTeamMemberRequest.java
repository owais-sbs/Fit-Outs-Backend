package com.fitouts.subcontractor.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScAddTeamMemberRequest {

    private String email;
    private String fullName;
    private String phone;
    private String portalRole;
    private String password;
    private List<String> permissions;
}
