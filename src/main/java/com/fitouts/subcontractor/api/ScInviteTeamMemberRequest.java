package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScInviteTeamMemberRequest {

    private String email;
    private String fullName;
    private String phone;
    private String portalRole;
}
