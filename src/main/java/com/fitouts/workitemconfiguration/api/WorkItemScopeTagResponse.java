package com.fitouts.workitemconfiguration.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WorkItemScopeTagResponse {
    private UUID id;
    private String code;
    private String name;
}
