package com.fitouts.approvalconfig.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProjectPermitDocumentResponse {
    private UUID id;
    private UUID documentTypeId;
    private String docCode;
    private String documentName;
    private String category;
    private String sourceOwner;
    private boolean expiryTracked;
    private String status;
}
