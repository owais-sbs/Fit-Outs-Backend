package com.fitouts.snag.api;

import com.fitouts.snag.domain.SnagStatus;

import lombok.Data;

@Data
public class SnagStatusRequest {
    private SnagStatus status;
}
