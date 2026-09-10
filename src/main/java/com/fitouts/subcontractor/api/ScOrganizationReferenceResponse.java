package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScOrganizationReferenceResponse {

    private final UUID uuid;
    private final String clientName;
    private final BigDecimal projectValue;
    private final Integer yearCompleted;
    private final String scopeDescription;
    private final String contactName;
    private final String contactPhone;
    private final String contactEmail;
}
