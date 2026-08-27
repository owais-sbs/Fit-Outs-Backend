package com.fitouts.appendix.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppendixMasterRequest {

    private String title;
    private String description;
    private String category;
    private Integer sortOrder;
    private Boolean active;
}
