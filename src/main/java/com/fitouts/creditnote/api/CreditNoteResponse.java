package com.fitouts.creditnote.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.creditnote.domain.CreditNoteStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreditNoteResponse {
    private UUID uuid;
    private Long projectId;
    private String noteNumber;
    private String title;
    private String reason;
    private BigDecimal amount;
    private CreditNoteStatus status;
    private UUID approvalRunUuid;
    private String rejectComment;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;
}
