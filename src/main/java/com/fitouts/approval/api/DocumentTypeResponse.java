package com.fitouts.approval.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DocumentTypeResponse {

    private UUID uuid;
    private String code;
    private String name;
    private String category;
    private String typicallyRequiredFor;
    private boolean expiryTracked;
    private String ownerRole;
    private String sourceOwner;
    private boolean active;
    private boolean tenantOwned;
}
