package com.fitouts.variation.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.commercialapproval.api.ApprovalRunResponse;
import com.fitouts.variation.domain.VariationCostMode;
import com.fitouts.variation.domain.VariationLineType;
import com.fitouts.variation.domain.VariationLinkType;
import com.fitouts.variation.domain.VariationOrigin;
import com.fitouts.variation.domain.VariationReasonCode;
import com.fitouts.variation.domain.VariationStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariationResponse {
    private UUID uuid;
    private Long projectId;
    private String projectName;
    private String crNumber;
    private VariationOrigin origin;
    private VariationStatus status;
    private String title;
    private String description;
    private VariationReasonCode reasonCode;
    private VariationCostMode costMode;
    private BigDecimal sellDelta;
    private BigDecimal costDelta;
    private BigDecimal marginDelta;
    private Integer proposedDelayDays;
    private boolean applyScheduleOnApproval;
    private OffsetDateTime lockedAt;
    private Long raisedBy;
    private Long submittedBy;
    private OffsetDateTime submittedAt;
    private Long issuedBy;
    private OffsetDateTime issuedAt;
    private Long approvedBy;
    private OffsetDateTime approvedAt;
    private String triageNote;
    private String rejectComment;
    private UUID approvalRunUuid;
    private ApprovalRunResponse approvalRun;
    private BigDecimal currentContractValue;
    private BigDecimal proposedContractValue;
    private BigDecimal currentMargin;
    private BigDecimal proposedMargin;
    private List<LineResponse> lines;
    private List<LinkResponse> links;
    private List<AttachmentResponse> attachments;
    private List<EventResponse> events;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @Builder
    public static class LineResponse {
        private UUID uuid;
        private VariationLineType lineType;
        private UUID sourceBoqLineId;
        private UUID workItemId;
        private String description;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal sellRate;
        private BigDecimal sellAmount;
        private BigDecimal costRate;
        private BigDecimal costAmount;
        private int sortOrder;
    }

    @Data
    @Builder
    public static class LinkResponse {
        private UUID uuid;
        private VariationLinkType linkType;
        private UUID roomId;
        private UUID activityUuid;
    }

    @Data
    @Builder
    public static class AttachmentResponse {
        private UUID uuid;
        private String filePath;
        private String originalName;
        private Long uploadedBy;
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    public static class EventResponse {
        private UUID uuid;
        private String action;
        private String fromStatus;
        private String toStatus;
        private Long actorId;
        private String detail;
        private BigDecimal previousContractValue;
        private BigDecimal newContractValue;
        private BigDecimal previousMargin;
        private BigDecimal newMargin;
        private OffsetDateTime createdAt;
    }
}
