package com.fitouts.holdpoint.api;

import java.util.List;

import lombok.Data;

@Data
public class QualityTemplateRequest {
    private List<String> checklistItems;
    private String checklistJson;
}
