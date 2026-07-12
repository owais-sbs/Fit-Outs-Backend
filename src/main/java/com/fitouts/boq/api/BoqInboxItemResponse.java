package com.fitouts.boq.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.BoqDocumentStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqInboxItemResponse {
    private UUID id;
    private Long projectId;
    private String projectName;
    private String version;
    private BoqDocumentStatus status;
    private String currentApprovalStep;
    private BigDecimal grandTotal;
    private LocalDateTime submittedAt;
    private String submittedByName;
}
