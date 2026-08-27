package com.fitouts.projectdoc.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ProjectDocumentRequest {
    private String title;
    private String category;
    private String filePath;
    private UUID parentDocumentUuid;
    private Boolean publishedToClient;
}
