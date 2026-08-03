package com.fitouts.checklist.mapper;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.domain.ChecklistTemplateItem;
import com.fitouts.checklist.dto.ChecklistTemplateItemRequest;
import com.fitouts.checklist.dto.ChecklistTemplateItemResponse;
import com.fitouts.checklist.dto.ChecklistTemplateRequest;
import com.fitouts.checklist.dto.ChecklistTemplateResponse;

@Component
public class ChecklistTemplateMapper {

    public ChecklistTemplate toEntity(ChecklistTemplateRequest request) {
        ChecklistTemplate template = new ChecklistTemplate();
        template.setName(request.getName().trim());
        template.setDescription(trimNullable(request.getDescription()));
        template.setCreatedBy(request.getCreatedBy());
        request.getItems().stream()
                .map(this::toItem)
                .forEach(template::addItem);
        return template;
    }

    public ChecklistTemplateResponse toResponse(ChecklistTemplate template) {
        ChecklistTemplateResponse response = ChecklistTemplateResponse.builder()
                .name(template.getName())
                .description(template.getDescription())
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .items(template.getItems().stream()
                        .sorted(Comparator.comparing(ChecklistTemplateItem::getDisplayOrder)
                                .thenComparing(ChecklistTemplateItem::getUuid, Comparator.nullsLast(java.util.UUID::compareTo)))
                        .map(this::toItemResponse)
                        .toList())
                .build();
        response.setUuid(template.getUuid());
        return response;
    }

    private ChecklistTemplateItem toItem(ChecklistTemplateItemRequest request) {
        ChecklistTemplateItem item = new ChecklistTemplateItem();
        item.setSectionName(request.getSectionName().trim());
        item.setRoomName(trimNullable(request.getRoomName()));
        item.setQuestion(request.getQuestion().trim());
        item.setType(request.getType());
        item.setIsRequired(request.getIsRequired());
        item.setDisplayOrder(request.getDisplayOrder());
        return item;
    }

    private ChecklistTemplateItemResponse toItemResponse(ChecklistTemplateItem item) {
        ChecklistTemplateItemResponse response = ChecklistTemplateItemResponse.builder()
                .sectionName(item.getSectionName())
                .roomName(item.getRoomName())
                .question(item.getQuestion())
                .type(item.getType())
                .isRequired(item.getIsRequired())
                .displayOrder(item.getDisplayOrder())
                .build();
        response.setUuid(item.getUuid());
        return response;
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
