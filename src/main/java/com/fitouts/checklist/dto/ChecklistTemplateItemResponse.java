package com.fitouts.checklist.dto;

import com.fitouts.checklist.domain.ChecklistItemType;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ChecklistTemplateItemResponse {

    private UUID uuid;
    private String sectionName;
    private String roomName;
    private String question;
    private ChecklistItemType type;
    private Boolean isRequired;
    private Integer displayOrder;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
