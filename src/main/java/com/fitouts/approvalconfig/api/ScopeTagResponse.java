package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ScopeTagResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private boolean active;
    private LocalDateTime updatedAt;
    private List<UUID> permitTypeIds;
    private List<LinkedPermitTypeResponse> triggeredPermits;
}
