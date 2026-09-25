package com.fitouts.schedule.api;

import com.fitouts.schedule.domain.DelayReasonCode;

import lombok.Data;

@Data
public class DurationExtensionCreateRequest {
    private Integer requestedDurationWorkingDays;
    private DelayReasonCode delayReasonCode;
    private String delayReasonText;
}
