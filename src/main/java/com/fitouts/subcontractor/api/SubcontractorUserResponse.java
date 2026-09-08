package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorUserResponse {

    private UUID id;
    private UUID subcontractorId;
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private String permissionsJson;
    private Long invitedBy;
    private String status;
}
