package com.fitouts.approvalconfig.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScopeTagRequest {
    private String code;
    private String name;
    private String description;
    private Boolean active;
}
