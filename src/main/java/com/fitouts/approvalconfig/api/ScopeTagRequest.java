package com.fitouts.approvalconfig.api;

import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScopeTagRequest {
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private List<UUID> permitTypeIds;
}
