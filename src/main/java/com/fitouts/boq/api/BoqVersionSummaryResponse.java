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
public class BoqVersionSummaryResponse {
    private UUID id;
    private String version;
    private String revisionLabel;
    private BoqDocumentStatus status;
    private BigDecimal grandTotal;
    private LocalDateTime createdAt;
}
