package com.fitouts.subcontractor.api;

import lombok.Data;

@Data
public class ScNotificationPrefRequest {
    private boolean whatsappEnabled;
    private String whatsappNumber;
    private String preferredLanguage;
}
