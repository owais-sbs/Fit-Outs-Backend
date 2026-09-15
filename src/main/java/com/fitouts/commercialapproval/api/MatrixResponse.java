package com.fitouts.commercialapproval.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.auth.domain.Role;
import com.fitouts.commercialapproval.domain.ApprovalStepMode;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunStatus;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatrixResponse {
    private UUID uuid;
    private CommercialEventType eventType;
    private String name;
    private boolean active;
    private List<BandResponse> bands;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @Builder
    public static class BandResponse {
        private UUID uuid;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private int sortOrder;
        private List<StepResponse> steps;
    }

    @Data
    @Builder
    public static class StepResponse {
        private UUID uuid;
        private int stepOrder;
        private ApprovalStepMode mode;
        private int slaHours;
        private Role escalateToRole;
        private List<Role> roles;
    }
}
