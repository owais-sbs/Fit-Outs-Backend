package com.fitouts.approvalconfig.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ApprovalCatalogBundleResponse {
    private List<AuthorityResponse> authorities;
    private List<PermitTypeResponse> permitTypes;
    private List<DocumentTypeResponse> documentTypes;
    private List<ScopeTagResponse> scopeTags;
    private List<JurisdictionPackResponse> packs;
}
