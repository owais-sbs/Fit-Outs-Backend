package com.fitouts.variation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitouts.variation.domain.VariationCostMode;
import com.fitouts.variation.domain.VariationLineType;
import com.fitouts.variation.domain.VariationLinkType;
import com.fitouts.variation.domain.VariationReasonCode;

import lombok.Data;

@Data
public class VariationUpsertRequest {
    private String title;
    private String description;
    private VariationReasonCode reasonCode;
    private VariationCostMode costMode;
    private Integer proposedDelayDays;
    private Boolean applyScheduleOnApproval;
    private List<LineRequest> lines;
    private List<LinkRequest> links;

    @Data
    public static class LineRequest {
        private UUID uuid;
        private VariationLineType lineType;
        private UUID sourceBoqLineId;
        private UUID workItemId;
        private String description;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal sellRate;
        private BigDecimal costRate;
        private Integer sortOrder;
    }

    @Data
    public static class LinkRequest {
        private VariationLinkType linkType;
        private UUID roomId;
        private UUID activityUuid;
    }
}
