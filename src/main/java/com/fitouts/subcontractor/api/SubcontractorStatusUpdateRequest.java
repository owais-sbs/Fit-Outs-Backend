package com.fitouts.subcontractor.api;

import com.fitouts.subcontractor.domain.SubcontractorStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorStatusUpdateRequest {

    private SubcontractorStatus status;
    private String notes;
}
