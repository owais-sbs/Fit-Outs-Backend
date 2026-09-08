package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DocumentTypeResponse {
    private UUID id;
    private String docCode;
    private String name;
    private String category;
    private String typicallyRequiredFor;
    private boolean expiryTracked;
    private String sourceOwner;
    private boolean active;
    private LocalDateTime updatedAt;
}
