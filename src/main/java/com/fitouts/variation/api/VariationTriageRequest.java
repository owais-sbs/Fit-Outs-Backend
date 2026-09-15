package com.fitouts.variation.api;

import lombok.Data;

@Data
public class VariationTriageRequest {
    private boolean accept;
    private String note;
}
