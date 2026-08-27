package com.fitouts.subcontractor.api;

import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.Data;

@Data
public class SubcontractorPackageRequest {
    private String name;
    private String boqSectionCode;
    private SubcontractorPackageStatus status;
}
