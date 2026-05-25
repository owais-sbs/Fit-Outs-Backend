package com.fitouts.checklist.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChecklistTemplateRequest {

    @NotBlank
    private String name;

    private String description;

    private Long createdBy;

    @NotEmpty
    private List<@Valid ChecklistTemplateItemRequest> items;
}
