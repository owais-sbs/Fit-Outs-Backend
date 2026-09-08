package com.fitouts.approvalconfig.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PackPermitResponse {
    private UUID id;
    private UUID permitTypeId;
    private String permitCode;
    private String permitName;
    private UUID issuingAuthorityId;
    private String issuingAuthorityName;
    private String inclusionRule;
    private int sortOrder;
    @Builder.Default
    private List<PackDocumentResponse> documents = new ArrayList<>();
}
