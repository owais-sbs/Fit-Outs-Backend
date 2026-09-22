package com.fitouts.boq.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.BoqDocumentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqPortfolioDocumentResponse {
    private UUID id;
    private Long projectId;
    private String version;
    private BoqDocumentStatus status;
    private String currentApprovalStep;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
