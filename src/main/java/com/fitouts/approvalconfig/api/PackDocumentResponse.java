package com.fitouts.approvalconfig.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PackDocumentResponse {
    private UUID id;
    private UUID documentTypeId;
    private String docCode;
    private String documentName;
}
