package com.fitouts.holdpoint.domain;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityQualityTemplateId implements Serializable {
    private UUID companyId;
    private String activityType;
}
