package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScAwardPackResponse {
    private UUID uuid;
    private UUID packageUuid;
    private String packageName;
    private UUID organizationUuid;
    private String organizationName;
    private UUID quoteUuid;
    private BigDecimal awardedValue;
    private OffsetDateTime awardedAt;
    private String contractFilePath;
}
