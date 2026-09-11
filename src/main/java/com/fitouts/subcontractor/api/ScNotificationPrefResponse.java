package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScNotificationPrefResponse {
    private UUID organizationUuid;
    private boolean whatsappEnabled;
    private String whatsappNumber;
    private String preferredLanguage;
    private OffsetDateTime updatedAt;
}
