package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScWorkerResponse {

    private final UUID uuid;
    private final String fullName;
    private final String trade;
    private final String passportNumber;
    private final String visaExpiry;
    private final String emiratesIdExpiry;
    private final String insuranceExpiry;
    private final String inductionDate;
    private final String accessCardExpiry;
    private final String tradeCertExpiry;
    private final String accessCardNumber;
    private final boolean inductionCompleted;
    private final boolean active;
    private final boolean siteEligible;
    private final String siteEligibilityNote;
}
