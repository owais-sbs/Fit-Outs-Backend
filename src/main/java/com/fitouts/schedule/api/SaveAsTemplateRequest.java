package com.fitouts.schedule.api;

import lombok.Data;

@Data
public class SaveAsTemplateRequest {
    /** Display name for the new tenant template. */
    private String name;
    /** Short code (unique per tenant). Generated from name when blank. */
    private String code;
}
