package com.fitouts.checklist.dto;

import com.fitouts.checklist.domain.ChecklistItemType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChecklistTemplateItemRequest {

    @NotBlank
    private String sectionName;

    private String roomName;

    @NotBlank
    private String question;

    @NotNull
    private ChecklistItemType type;

    @NotNull
    private Boolean isRequired;

    @NotNull
    @PositiveOrZero
    private Integer displayOrder;
}
