package com.fitouts.approvalconfig.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackPermitLineRequest {
    private UUID permitTypeId;
    private UUID issuingAuthorityId;
    private String inclusionRule;
    private Integer sortOrder;
    private List<UUID> documentTypeIds = new ArrayList<>();
}
