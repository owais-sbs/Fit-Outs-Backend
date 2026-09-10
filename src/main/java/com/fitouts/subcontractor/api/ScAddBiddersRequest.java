package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class ScAddBiddersRequest {
    private List<UUID> organizationUuids;
}
