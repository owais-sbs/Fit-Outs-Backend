package com.fitouts.projectdoc.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectDocumentResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String title;
    private String category;
    private String filePath;
    private int version;
    private boolean publishedToClient;
    private Long uploadedBy;
    private UUID parentDocumentUuid;
    private String sourceType;
    private UUID sourceUuid;
    private boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
