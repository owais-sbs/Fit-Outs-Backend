package com.fitouts.approval.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DerivedScopeTagView {
    private String code;
    private String name;
}
