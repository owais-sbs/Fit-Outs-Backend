package com.fitouts.appendix.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AppendixMasterResponse {

    private UUID uuid;
    private String title;
    private String description;
    private String imageUrl;
    private String category;
    private Integer sortOrder;
    private Boolean active;
    private OffsetDateTime createdAt;
}
