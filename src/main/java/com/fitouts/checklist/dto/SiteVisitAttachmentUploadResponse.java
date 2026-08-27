package com.fitouts.checklist.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SiteVisitAttachmentUploadResponse {
    private String url;
    private String contentType;
}
