package com.fitouts.approvalconfig.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approvalconfig.api.ApprovalCatalogBundleResponse;
import com.fitouts.approvalconfig.api.AuthorityRequest;
import com.fitouts.approvalconfig.api.AuthorityResponse;
import com.fitouts.approvalconfig.api.DocumentTypeRequest;
import com.fitouts.approvalconfig.api.DocumentTypeResponse;
import com.fitouts.approvalconfig.api.PermitTypeRequest;
import com.fitouts.approvalconfig.api.PermitTypeResponse;
import com.fitouts.approvalconfig.api.ScopeTagRequest;
import com.fitouts.approvalconfig.api.ScopeTagResponse;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalAuthorityRepository;
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalScopeTagRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalCatalogService {

    private final ApprovalConfigSeedService seedService;
    private final ApprovalAuthorityRepository authorityRepository;
    private final ApprovalPermitTypeRepository permitTypeRepository;
    private final ApprovalDocumentTypeRepository documentTypeRepository;
    private final ApprovalScopeTagRepository scopeTagRepository;
    private final JurisdictionPackService packService;

    public ApprovalCatalogBundleResponse loadCatalog() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return ApprovalCatalogBundleResponse.builder()
                .authorities(authorityRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(this::toAuthority)
                        .toList())
                .permitTypes(permitTypeRepository.findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                        .map(this::toPermit)
                        .toList())
                .documentTypes(documentTypeRepository.findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                        .map(this::toDocument)
                        .toList())
                .scopeTags(scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(this::toScopeTag)
                        .toList())
                .packs(packService.list(false))
                .build();
    }

    @Transactional
    public List<AuthorityResponse> listAuthorities() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return authorityRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(this::toAuthority)
                .toList();
    }

    @Transactional
    public AuthorityResponse createAuthority(AuthorityRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalAuthority entity = new ApprovalAuthority();
        entity.setCompanyId(companyId);
        applyAuthority(entity, request, true);
        return toAuthority(authorityRepository.save(entity));
    }

    @Transactional
    public AuthorityResponse updateAuthority(UUID id, AuthorityRequest request) {
        ApprovalAuthority entity = authority(id);
        applyAuthority(entity, request, false);
        return toAuthority(authorityRepository.save(entity));
    }

    @Transactional
    public void deleteAuthority(UUID id) {
        ApprovalAuthority entity = authority(id);
        entity.setDeleted(true);
        entity.setActive(false);
        authorityRepository.save(entity);
    }

    @Transactional
    public List<PermitTypeResponse> listPermitTypes() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return permitTypeRepository.findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                .map(this::toPermit)
                .toList();
    }

    @Transactional
    public PermitTypeResponse createPermitType(PermitTypeRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalPermitType entity = new ApprovalPermitType();
        entity.setCompanyId(companyId);
        applyPermit(entity, request, true);
        return toPermit(permitTypeRepository.save(entity));
    }

    @Transactional
    public PermitTypeResponse updatePermitType(UUID id, PermitTypeRequest request) {
        ApprovalPermitType entity = permit(id);
        applyPermit(entity, request, false);
        return toPermit(permitTypeRepository.save(entity));
    }

    @Transactional
    public void deletePermitType(UUID id) {
        ApprovalPermitType entity = permit(id);
        entity.setDeleted(true);
        entity.setActive(false);
        permitTypeRepository.save(entity);
    }

    @Transactional
    public List<DocumentTypeResponse> listDocumentTypes() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return documentTypeRepository.findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                .map(this::toDocument)
                .toList();
    }

    @Transactional
    public DocumentTypeResponse createDocumentType(DocumentTypeRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalDocumentType entity = new ApprovalDocumentType();
        entity.setCompanyId(companyId);
        applyDocument(entity, request, true);
        return toDocument(documentTypeRepository.save(entity));
    }

    @Transactional
    public DocumentTypeResponse updateDocumentType(UUID id, DocumentTypeRequest request) {
        ApprovalDocumentType entity = document(id);
        applyDocument(entity, request, false);
        return toDocument(documentTypeRepository.save(entity));
    }

    @Transactional
    public void deleteDocumentType(UUID id) {
        ApprovalDocumentType entity = document(id);
        entity.setDeleted(true);
        entity.setActive(false);
        documentTypeRepository.save(entity);
    }

    @Transactional
    public List<ScopeTagResponse> listScopeTags() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(this::toScopeTag)
                .toList();
    }

    @Transactional
    public ScopeTagResponse createScopeTag(ScopeTagRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalScopeTag entity = new ApprovalScopeTag();
        entity.setCompanyId(companyId);
        applyScopeTag(entity, request, true);
        return toScopeTag(scopeTagRepository.save(entity));
    }

    @Transactional
    public ScopeTagResponse updateScopeTag(UUID id, ScopeTagRequest request) {
        ApprovalScopeTag entity = scopeTag(id);
        applyScopeTag(entity, request, false);
        return toScopeTag(scopeTagRepository.save(entity));
    }

    @Transactional
    public void deleteScopeTag(UUID id) {
        ApprovalScopeTag entity = scopeTag(id);
        entity.setDeleted(true);
        entity.setActive(false);
        scopeTagRepository.save(entity);
    }

    private void applyAuthority(ApprovalAuthority entity, AuthorityRequest request, boolean creating) {
        if (creating || StringUtils.hasText(request.getCode())) {
            entity.setCode(request.getCode().trim().toUpperCase());
        }
        if (creating || StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (creating || StringUtils.hasText(request.getType())) {
            entity.setType(request.getType().trim());
        }
        if (request.getEmirate() != null) {
            entity.setEmirate(request.getEmirate());
        }
        if (request.getJurisdictionAreas() != null) {
            entity.setJurisdictionAreas(request.getJurisdictionAreas());
        }
        if (request.getPermitsIssued() != null) {
            entity.setPermitsIssued(request.getPermitsIssued());
        }
        if (request.getSubmissionChannel() != null) {
            entity.setSubmissionChannel(request.getSubmissionChannel());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    private void applyPermit(ApprovalPermitType entity, PermitTypeRequest request, boolean creating) {
        if (creating || StringUtils.hasText(request.getPermitCode())) {
            entity.setPermitCode(request.getPermitCode().trim().toUpperCase());
        }
        if (creating || StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getIssuingBody() != null) {
            entity.setIssuingBody(request.getIssuingBody());
        }
        if (request.getTypicalTrigger() != null) {
            entity.setTypicalTrigger(request.getTypicalTrigger());
        }
        if (request.getPrerequisiteCases() != null) {
            entity.setPrerequisiteCases(request.getPrerequisiteCases());
        }
        if (request.getSlaWorkingDays() != null) {
            entity.setSlaWorkingDays(request.getSlaWorkingDays());
        }
        if (request.getTypicalValidity() != null) {
            entity.setTypicalValidity(request.getTypicalValidity());
        }
        if (request.getDeposit() != null) {
            entity.setDeposit(request.getDeposit());
        }
        if (request.getRenewable() != null) {
            entity.setRenewable(request.getRenewable());
        }
        if (request.getBlocksActivities() != null) {
            entity.setBlocksActivities(request.getBlocksActivities());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    private void applyDocument(ApprovalDocumentType entity, DocumentTypeRequest request, boolean creating) {
        if (creating || StringUtils.hasText(request.getDocCode())) {
            entity.setDocCode(request.getDocCode().trim().toUpperCase());
        }
        if (creating || StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getCategory() != null) {
            entity.setCategory(request.getCategory());
        }
        if (request.getTypicallyRequiredFor() != null) {
            entity.setTypicallyRequiredFor(request.getTypicallyRequiredFor());
        }
        if (request.getExpiryTracked() != null) {
            entity.setExpiryTracked(request.getExpiryTracked());
        }
        if (request.getSourceOwner() != null) {
            entity.setSourceOwner(request.getSourceOwner());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    private ApprovalAuthority authority(UUID id) {
        ApprovalAuthority entity = authorityRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Authority not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private ApprovalPermitType permit(UUID id) {
        ApprovalPermitType entity = permitTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Permit type not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private ApprovalDocumentType document(UUID id) {
        ApprovalDocumentType entity = documentTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Document type not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private void applyScopeTag(ApprovalScopeTag entity, ScopeTagRequest request, boolean creating) {
        if (creating || StringUtils.hasText(request.getCode())) {
            entity.setCode(request.getCode().trim().toUpperCase());
        }
        if (creating || StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    private ApprovalScopeTag scopeTag(UUID id) {
        ApprovalScopeTag entity = scopeTagRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Scope tag not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private AuthorityResponse toAuthority(ApprovalAuthority entity) {
        return AuthorityResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .type(entity.getType())
                .emirate(entity.getEmirate())
                .jurisdictionAreas(entity.getJurisdictionAreas())
                .permitsIssued(entity.getPermitsIssued())
                .submissionChannel(entity.getSubmissionChannel())
                .notes(entity.getNotes())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PermitTypeResponse toPermit(ApprovalPermitType entity) {
        return PermitTypeResponse.builder()
                .id(entity.getId())
                .permitCode(entity.getPermitCode())
                .name(entity.getName())
                .issuingBody(entity.getIssuingBody())
                .typicalTrigger(entity.getTypicalTrigger())
                .prerequisiteCases(entity.getPrerequisiteCases())
                .slaWorkingDays(entity.getSlaWorkingDays())
                .typicalValidity(entity.getTypicalValidity())
                .deposit(entity.getDeposit())
                .renewable(entity.getRenewable())
                .blocksActivities(entity.getBlocksActivities())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DocumentTypeResponse toDocument(ApprovalDocumentType entity) {
        return DocumentTypeResponse.builder()
                .id(entity.getId())
                .docCode(entity.getDocCode())
                .name(entity.getName())
                .category(entity.getCategory())
                .typicallyRequiredFor(entity.getTypicallyRequiredFor())
                .expiryTracked(entity.isExpiryTracked())
                .sourceOwner(entity.getSourceOwner())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ScopeTagResponse toScopeTag(ApprovalScopeTag entity) {
        return ScopeTagResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new NotFoundException("Company context is required");
        }
        return companyId;
    }

    private void assertCompany(UUID companyId) {
        if (!requireCompany().equals(companyId)) {
            throw new NotFoundException("Record not found");
        }
    }
}
