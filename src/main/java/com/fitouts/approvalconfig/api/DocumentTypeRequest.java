package com.fitouts.approvalconfig.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentTypeRequest {
    private String docCode;
    private String name;
    private String category;
    private String typicallyRequiredFor;
    private Boolean expiryTracked;
    private String sourceOwner;
    private Boolean active;
}
