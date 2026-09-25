package com.fitouts.boq.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
public class BoqPortfolioProjectResponse {
    private Long projectId;
    private String projectName;
    private String projectType;
    private String location;
    private String status;
    private Integer progress;
    private BigDecimal budget;
    private BigDecimal boqTotal;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private BigDecimal variance;
    private UUID boqId;
    private BoqDocumentStatus boqStatus;
    private String boqVersion;
    private String currentApprovalStep;
    private BigDecimal currentContractValue;
    private LocalDate startDate;
    private LocalDateTime createdAt;
    private List<BoqPortfolioDocumentResponse> boqs;
}
