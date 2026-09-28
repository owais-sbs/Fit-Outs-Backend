package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BoqMatchSuggestionResponse {
    private UUID boqLineId;
    private Integer sortOrder;
    private String description;
    private String categoryCode;
    private String categoryName;
    private String suggestedActivityCode;
    private String suggestedActivityName;
    private double confidence;
}
