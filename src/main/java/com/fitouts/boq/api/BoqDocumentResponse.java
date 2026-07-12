package com.fitouts.boq.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.shared.enums.BoqDocumentStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqDocumentResponse {
    private UUID id;
    private Long projectId;
    private String projectName;
    private UUID qtoSessionId;
    private UUID parentBoqId;
    private String version;
    private String revisionLabel;
    private BoqDocumentStatus status;
    private String currentApprovalStep;
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal grandTotal;
    private String notes;
    private String lastRejectionComment;
    private List<BoqLineResponse> lines;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
