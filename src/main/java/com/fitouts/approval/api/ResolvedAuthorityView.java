package com.fitouts.approval.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResolvedAuthorityView {

    private String code;
    private String name;
    private String type;
    private String layer;
    /** Why this authority was included, shown so the resolver is never a black box. */
    private String reason;
}
