package com.fitouts.approvalconfig.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermitCaseStatusRequest {
    private String status;
    private String notes;
}
