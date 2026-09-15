package com.fitouts.commercialapproval.api;

import java.math.BigDecimal;
import java.util.List;

import com.fitouts.auth.domain.Role;
import com.fitouts.commercialapproval.domain.ApprovalStepMode;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.Data;

@Data
public class MatrixUpsertRequest {
    private CommercialEventType eventType;
    private String name;
    private Boolean active;
    private List<BandRequest> bands;

    @Data
    public static class BandRequest {
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Integer sortOrder;
        private List<StepRequest> steps;
    }

    @Data
    public static class StepRequest {
        private Integer stepOrder;
        private ApprovalStepMode mode;
        private Integer slaHours;
        private Role escalateToRole;
        private List<Role> roles;
    }
}
