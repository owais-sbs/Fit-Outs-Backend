package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ScNominateWorkerRequest {
    private UUID workerUuid;
    private String notes;
}
