package com.fitouts.approvalconfig.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approvalconfig.api.ApprovalCatalogBundleResponse;
import com.fitouts.approvalconfig.api.AuthorityRequest;
import com.fitouts.approvalconfig.api.AuthorityResponse;
import com.fitouts.approvalconfig.api.CatalogItemRequest;
import com.fitouts.approvalconfig.api.CatalogItemResponse;
import com.fitouts.approvalconfig.api.CatalogLinkResponse;
import com.fitouts.approvalconfig.api.CompanyRegistrationRequest;
import com.fitouts.approvalconfig.api.CompanyRegistrationResponse;
import com.fitouts.approvalconfig.api.DocumentTypeRequest;
import com.fitouts.approvalconfig.api.DocumentTypeResponse;
import com.fitouts.approvalconfig.api.LinkedPermitTypeResponse;
import com.fitouts.approvalconfig.api.LinkedScopeTagResponse;
import com.fitouts.approvalconfig.api.PermitTypeRequest;
import com.fitouts.approvalconfig.api.PermitTypeResponse;
import com.fitouts.approvalconfig.api.ScopeTagRequest;
import com.fitouts.approvalconfig.api.ScopeTagResponse;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalAuthorityRepository;
import com.fitouts.approvalconfig.domain.ApprovalCompanyRegistration;
import com.fitouts.approvalconfig.domain.ApprovalCompanyRegistrationRepository;
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalPermitProjectNatureRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyType;
import com.fitouts.approvalconfig.domain.ApprovalPermitPropertyTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTagRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalProjectNatureRepository;
import com.fitouts.approvalconfig.domain.ApprovalPropertyType;
import com.fitouts.approvalconfig.domain.ApprovalPropertyTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalScopeTagRepository;
import com.fitouts.approvalconfig.domain.PermitTriggerTypes;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
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
    private final ApprovalPropertyTypeRepository propertyTypeRepository;
    private final ApprovalProjectNatureRepository projectNatureRepository;
    private final ApprovalPermitScopeTagRepository permitScopeTagRepository;
    private final ApprovalPermitPropertyTypeRepository permitPropertyTypeRepository;
    private final ApprovalPermitProjectNatureRepository permitProjectNatureRepository;
    private final ApprovalCompanyRegistrationRepository companyRegistrationRepository;
    private final JurisdictionPackService packService;

    @Transactional
    public ApprovalCatalogBundleResponse loadCatalog() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        CatalogLinks links = loadLinks(companyId);
        return ApprovalCatalogBundleResponse.builder()
                .authorities(authorityRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(this::toAuthority)
                        .toList())
                .permitTypes(permitTypeRepository.findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                        .map(p -> toPermit(p, links))
                        .toList())
                .documentTypes(documentTypeRepository.findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(companyId).stream()
                        .map(this::toDocument)
                        .toList())
                .scopeTags(scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(t -> toScopeTag(t, links))
                        .toList())
                .propertyTypes(propertyTypeRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(t -> toPropertyType(t, links))
                        .toList())
                .projectNatures(projectNatureRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                        .map(t -> toProjectNature(t, links))
                        .toList())
                .companyRegistrations(listCompanyRegistrations(companyId))
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
        CatalogLinks links = loadLinks(companyId);
        return permitTypeRepository.findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId).stream()
                .map(p -> toPermit(p, links))
                .toList();
    }

    @Transactional
    public PermitTypeResponse createPermitType(PermitTypeRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalPermitType entity = new ApprovalPermitType();
        entity.setCompanyId(companyId);
        applyPermit(entity, request, true);
        entity = permitTypeRepository.save(entity);
        replaceClassifierLinks(entity, request);
        return toPermit(entity, loadLinks(companyId));
    }

    @Transactional
    public PermitTypeResponse updatePermitType(UUID id, PermitTypeRequest request) {
        ApprovalPermitType entity = permit(id);
        applyPermit(entity, request, false);
        entity = permitTypeRepository.save(entity);
        replaceClassifierLinks(entity, request);
        return toPermit(entity, loadLinks(entity.getCompanyId()));
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
        CatalogLinks links = loadLinks(companyId);
        return scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(t -> toScopeTag(t, links))
                .toList();
    }

    @Transactional
    public ScopeTagResponse createScopeTag(ScopeTagRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalScopeTag entity = new ApprovalScopeTag();
        entity.setCompanyId(companyId);
        applyScopeTag(entity, request, true);
        entity = scopeTagRepository.save(entity);
        replacePermitsForScopeTag(entity, request.getPermitTypeIds());
        return toScopeTag(entity, loadLinks(companyId));
    }

    @Transactional
    public ScopeTagResponse updateScopeTag(UUID id, ScopeTagRequest request) {
        ApprovalScopeTag entity = scopeTag(id);
        applyScopeTag(entity, request, false);
        entity = scopeTagRepository.save(entity);
        replacePermitsForScopeTag(entity, request.getPermitTypeIds());
        return toScopeTag(entity, loadLinks(entity.getCompanyId()));
    }

    @Transactional
    public void deleteScopeTag(UUID id) {
        ApprovalScopeTag entity = scopeTag(id);
        entity.setDeleted(true);
        entity.setActive(false);
        scopeTagRepository.save(entity);
    }

    @Transactional
    public List<CatalogItemResponse> listPropertyTypes() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        CatalogLinks links = loadLinks(companyId);
        return propertyTypeRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(t -> toPropertyType(t, links))
                .toList();
    }

    @Transactional
    public CatalogItemResponse createPropertyType(CatalogItemRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalPropertyType entity = new ApprovalPropertyType();
        entity.setCompanyId(companyId);
        applyPropertyType(entity, request, true);
        entity = propertyTypeRepository.save(entity);
        replacePermitsForPropertyType(entity, request.getPermitTypeIds());
        return toPropertyType(entity, loadLinks(companyId));
    }

    @Transactional
    public CatalogItemResponse updatePropertyType(UUID id, CatalogItemRequest request) {
        ApprovalPropertyType entity = propertyType(id);
        applyPropertyType(entity, request, false);
        entity = propertyTypeRepository.save(entity);
        replacePermitsForPropertyType(entity, request.getPermitTypeIds());
        return toPropertyType(entity, loadLinks(entity.getCompanyId()));
    }

    @Transactional
    public void deletePropertyType(UUID id) {
        ApprovalPropertyType entity = propertyType(id);
        entity.setDeleted(true);
        entity.setActive(false);
        propertyTypeRepository.save(entity);
    }

    @Transactional
    public List<CatalogItemResponse> listProjectNatures() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        CatalogLinks links = loadLinks(companyId);
        return projectNatureRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(t -> toProjectNature(t, links))
                .toList();
    }

    @Transactional
    public CatalogItemResponse createProjectNature(CatalogItemRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        ApprovalProjectNature entity = new ApprovalProjectNature();
        entity.setCompanyId(companyId);
        applyProjectNature(entity, request, true);
        entity = projectNatureRepository.save(entity);
        replacePermitsForProjectNature(entity, request.getPermitTypeIds());
        return toProjectNature(entity, loadLinks(companyId));
    }

    @Transactional
    public CatalogItemResponse updateProjectNature(UUID id, CatalogItemRequest request) {
        ApprovalProjectNature entity = projectNature(id);
        applyProjectNature(entity, request, false);
        entity = projectNatureRepository.save(entity);
        replacePermitsForProjectNature(entity, request.getPermitTypeIds());
        return toProjectNature(entity, loadLinks(entity.getCompanyId()));
    }

    @Transactional
    public void deleteProjectNature(UUID id) {
        ApprovalProjectNature entity = projectNature(id);
        entity.setDeleted(true);
        entity.setActive(false);
        projectNatureRepository.save(entity);
    }

    @Transactional
    public List<CompanyRegistrationResponse> listCompanyRegistrations() {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        return listCompanyRegistrations(companyId);
    }

    @Transactional
    public CompanyRegistrationResponse createCompanyRegistration(CompanyRegistrationRequest request) {
        UUID companyId = requireCompany();
        seedService.ensureSeeded(companyId);
        if (request == null || request.getAuthorityId() == null) {
            throw new BadRequestException("Authority is required");
        }
        ApprovalAuthority authority = authority(request.getAuthorityId());
        companyRegistrationRepository.findByCompanyIdAndAuthorityIdAndDeletedFalse(companyId, authority.getId())
                .ifPresent(existing -> {
                    throw new BadRequestException("A registration for this authority already exists");
                });
        ApprovalCompanyRegistration entity = new ApprovalCompanyRegistration();
        entity.setCompanyId(companyId);
        entity.setAuthorityId(authority.getId());
        applyRegistration(entity, request);
        return toRegistration(companyRegistrationRepository.save(entity), authority);
    }

    @Transactional
    public CompanyRegistrationResponse updateCompanyRegistration(UUID id, CompanyRegistrationRequest request) {
        ApprovalCompanyRegistration entity = companyRegistration(id);
        if (request.getAuthorityId() != null) {
            ApprovalAuthority authority = authority(request.getAuthorityId());
            companyRegistrationRepository.findByCompanyIdAndAuthorityIdAndDeletedFalse(entity.getCompanyId(), authority.getId())
                    .filter(existing -> !existing.getId().equals(entity.getId()))
                    .ifPresent(existing -> {
                        throw new BadRequestException("A registration for this authority already exists");
                    });
            entity.setAuthorityId(authority.getId());
        }
        applyRegistration(entity, request);
        ApprovalAuthority authority = authority(entity.getAuthorityId());
        return toRegistration(companyRegistrationRepository.save(entity), authority);
    }

    @Transactional
    public void deleteCompanyRegistration(UUID id) {
        ApprovalCompanyRegistration entity = companyRegistration(id);
        entity.setDeleted(true);
        entity.setActive(false);
        companyRegistrationRepository.save(entity);
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
        if (request.getRequiresCompanyRegistration() != null) {
            entity.setRequiresCompanyRegistration(request.getRequiresCompanyRegistration());
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
        if (creating || request.getTriggerType() != null) {
            entity.setTriggerType(PermitTriggerTypes.normalize(request.getTriggerType()));
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
                .requiresCompanyRegistration(entity.isRequiresCompanyRegistration())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private PermitTypeResponse toPermit(ApprovalPermitType entity, CatalogLinks links) {
        List<ApprovalPermitScopeTag> rows = links.byPermit.getOrDefault(entity.getId(), List.of());
        List<LinkedScopeTagResponse> scopeTags = rows.stream()
                .map(row -> {
                    ApprovalScopeTag tag = links.tags.get(row.getScopeTagId());
                    if (tag == null || tag.isDeleted()) {
                        return null;
                    }
                    return LinkedScopeTagResponse.builder()
                            .id(tag.getId())
                            .code(tag.getCode())
                            .name(tag.getName())
                            .createdByName(row.getCreatedByName())
                            .createdAt(row.getCreatedAt())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(LinkedScopeTagResponse::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<CatalogLinkResponse> propertyTypes = links.propertyByPermit.getOrDefault(entity.getId(), List.of()).stream()
                .map(row -> {
                    ApprovalPropertyType item = links.propertyTypes.get(row.getPropertyTypeId());
                    if (item == null || item.isDeleted()) {
                        return null;
                    }
                    return CatalogLinkResponse.builder()
                            .id(item.getId())
                            .code(item.getCode())
                            .name(item.getName())
                            .createdByName(row.getCreatedByName())
                            .createdAt(row.getCreatedAt())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(CatalogLinkResponse::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<CatalogLinkResponse> projectNatures = links.natureByPermit.getOrDefault(entity.getId(), List.of()).stream()
                .map(row -> {
                    ApprovalProjectNature item = links.projectNatures.get(row.getProjectNatureId());
                    if (item == null || item.isDeleted()) {
                        return null;
                    }
                    return CatalogLinkResponse.builder()
                            .id(item.getId())
                            .code(item.getCode())
                            .name(item.getName())
                            .createdByName(row.getCreatedByName())
                            .createdAt(row.getCreatedAt())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(CatalogLinkResponse::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return PermitTypeResponse.builder()
                .id(entity.getId())
                .permitCode(entity.getPermitCode())
                .name(entity.getName())
                .issuingBody(entity.getIssuingBody())
                .typicalTrigger(entity.getTypicalTrigger())
                .triggerType(PermitTriggerTypes.normalize(entity.getTriggerType()))
                .prerequisiteCases(entity.getPrerequisiteCases())
                .slaWorkingDays(entity.getSlaWorkingDays())
                .typicalValidity(entity.getTypicalValidity())
                .deposit(entity.getDeposit())
                .renewable(entity.getRenewable())
                .blocksActivities(entity.getBlocksActivities())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .scopeTagIds(scopeTags.stream().map(LinkedScopeTagResponse::getId).toList())
                .scopeTags(scopeTags)
                .propertyTypeIds(propertyTypes.stream().map(CatalogLinkResponse::getId).toList())
                .propertyTypes(propertyTypes)
                .projectNatureIds(projectNatures.stream().map(CatalogLinkResponse::getId).toList())
                .projectNatures(projectNatures)
                .missingPrerequisite(PermitTriggerTypes.PREREQUISITE.equals(
                        PermitTriggerTypes.normalize(entity.getTriggerType()))
                        && !StringUtils.hasText(entity.getPrerequisiteCases()))
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

    private ScopeTagResponse toScopeTag(ApprovalScopeTag entity, CatalogLinks links) {
        List<ApprovalPermitScopeTag> rows = links.byTag.getOrDefault(entity.getId(), List.of());
        List<LinkedPermitTypeResponse> triggered = rows.stream()
                .map(row -> {
                    ApprovalPermitType permit = links.permits.get(row.getPermitTypeId());
                    if (permit == null || permit.isDeleted()) {
                        return null;
                    }
                    return LinkedPermitTypeResponse.builder()
                            .id(permit.getId())
                            .permitCode(permit.getPermitCode())
                            .name(permit.getName())
                            .createdByName(row.getCreatedByName())
                            .createdAt(row.getCreatedAt())
                            .build();
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(LinkedPermitTypeResponse::getPermitCode, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ScopeTagResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.isActive())
                .updatedAt(entity.getUpdatedAt())
                .permitTypeIds(triggered.stream().map(LinkedPermitTypeResponse::getId).toList())
                .triggeredPermits(triggered)
                .build();
    }

    private void replaceClassifierLinks(ApprovalPermitType permit, PermitTypeRequest request) {
        replaceScopeTagsForPermit(permit, request.getScopeTagIds());
        replacePropertyTypesForPermit(permit, request.getPropertyTypeIds());
        replaceProjectNaturesForPermit(permit, request.getProjectNatureIds());
    }

    private void replaceScopeTagsForPermit(ApprovalPermitType permit, List<UUID> scopeTagIds) {
        if (scopeTagIds == null) {
            return;
        }
        syncLinks(permit.getCompanyId(), permit.getId(), new LinkedHashSet<>(scopeTagIds), true);
    }

    private void replacePermitsForScopeTag(ApprovalScopeTag tag, List<UUID> permitTypeIds) {
        if (permitTypeIds == null) {
            return;
        }
        syncLinks(tag.getCompanyId(), tag.getId(), new LinkedHashSet<>(permitTypeIds), false);
    }

    private void replacePropertyTypesForPermit(ApprovalPermitType permit, List<UUID> ids) {
        if (ids == null) {
            return;
        }
        syncPropertyLinks(permit.getCompanyId(), permit.getId(), new LinkedHashSet<>(ids), true);
    }

    private void replacePermitsForPropertyType(ApprovalPropertyType item, List<UUID> permitTypeIds) {
        if (permitTypeIds == null) {
            return;
        }
        syncPropertyLinks(item.getCompanyId(), item.getId(), new LinkedHashSet<>(permitTypeIds), false);
    }

    private void replaceProjectNaturesForPermit(ApprovalPermitType permit, List<UUID> ids) {
        if (ids == null) {
            return;
        }
        syncNatureLinks(permit.getCompanyId(), permit.getId(), new LinkedHashSet<>(ids), true);
    }

    private void replacePermitsForProjectNature(ApprovalProjectNature item, List<UUID> permitTypeIds) {
        if (permitTypeIds == null) {
            return;
        }
        syncNatureLinks(item.getCompanyId(), item.getId(), new LinkedHashSet<>(permitTypeIds), false);
    }

    /**
     * Keeps existing rows (and their created_by trail) when the pair is still selected;
     * only inserts newly added pairs and deletes removed ones.
     */
    private void syncLinks(UUID companyId, UUID anchorId, Set<UUID> desiredOtherIds, boolean anchorIsPermit) {
        List<ApprovalPermitScopeTag> existing = anchorIsPermit
                ? permitScopeTagRepository.findByPermitTypeId(anchorId)
                : permitScopeTagRepository.findByScopeTagId(anchorId);

        Set<UUID> desired = desiredOtherIds.stream().filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (anchorIsPermit) {
            validateScopeTags(companyId, desired);
        } else {
            validatePermits(companyId, desired);
        }

        Set<UUID> keep = new HashSet<>();
        for (ApprovalPermitScopeTag row : existing) {
            UUID otherId = anchorIsPermit ? row.getScopeTagId() : row.getPermitTypeId();
            if (desired.contains(otherId)) {
                keep.add(otherId);
            } else {
                permitScopeTagRepository.delete(row);
            }
        }

        Actor actor = currentActor();
        for (UUID otherId : desired) {
            if (keep.contains(otherId)) {
                continue;
            }
            ApprovalPermitScopeTag row = new ApprovalPermitScopeTag();
            row.setCompanyId(companyId);
            if (anchorIsPermit) {
                row.setPermitTypeId(anchorId);
                row.setScopeTagId(otherId);
            } else {
                row.setPermitTypeId(otherId);
                row.setScopeTagId(anchorId);
            }
            row.setCreatedBy(actor.accountId());
            row.setCreatedByName(actor.name());
            permitScopeTagRepository.save(row);
        }
    }

    private void syncPropertyLinks(UUID companyId, UUID anchorId, Set<UUID> desiredOtherIds, boolean anchorIsPermit) {
        List<ApprovalPermitPropertyType> existing = anchorIsPermit
                ? permitPropertyTypeRepository.findByPermitTypeId(anchorId)
                : permitPropertyTypeRepository.findByPropertyTypeId(anchorId);
        Set<UUID> desired = desiredOtherIds.stream().filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (anchorIsPermit) {
            validatePropertyTypes(companyId, desired);
        } else {
            validatePermits(companyId, desired);
        }
        Set<UUID> keep = new HashSet<>();
        for (ApprovalPermitPropertyType row : existing) {
            UUID otherId = anchorIsPermit ? row.getPropertyTypeId() : row.getPermitTypeId();
            if (desired.contains(otherId)) {
                keep.add(otherId);
            } else {
                permitPropertyTypeRepository.delete(row);
            }
        }
        Actor actor = currentActor();
        for (UUID otherId : desired) {
            if (keep.contains(otherId)) {
                continue;
            }
            ApprovalPermitPropertyType row = new ApprovalPermitPropertyType();
            row.setCompanyId(companyId);
            if (anchorIsPermit) {
                row.setPermitTypeId(anchorId);
                row.setPropertyTypeId(otherId);
            } else {
                row.setPermitTypeId(otherId);
                row.setPropertyTypeId(anchorId);
            }
            row.setCreatedBy(actor.accountId());
            row.setCreatedByName(actor.name());
            permitPropertyTypeRepository.save(row);
        }
    }

    private void syncNatureLinks(UUID companyId, UUID anchorId, Set<UUID> desiredOtherIds, boolean anchorIsPermit) {
        List<ApprovalPermitProjectNature> existing = anchorIsPermit
                ? permitProjectNatureRepository.findByPermitTypeId(anchorId)
                : permitProjectNatureRepository.findByProjectNatureId(anchorId);
        Set<UUID> desired = desiredOtherIds.stream().filter(id -> id != null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (anchorIsPermit) {
            validateProjectNatures(companyId, desired);
        } else {
            validatePermits(companyId, desired);
        }
        Set<UUID> keep = new HashSet<>();
        for (ApprovalPermitProjectNature row : existing) {
            UUID otherId = anchorIsPermit ? row.getProjectNatureId() : row.getPermitTypeId();
            if (desired.contains(otherId)) {
                keep.add(otherId);
            } else {
                permitProjectNatureRepository.delete(row);
            }
        }
        Actor actor = currentActor();
        for (UUID otherId : desired) {
            if (keep.contains(otherId)) {
                continue;
            }
            ApprovalPermitProjectNature row = new ApprovalPermitProjectNature();
            row.setCompanyId(companyId);
            if (anchorIsPermit) {
                row.setPermitTypeId(anchorId);
                row.setProjectNatureId(otherId);
            } else {
                row.setPermitTypeId(otherId);
                row.setProjectNatureId(anchorId);
            }
            row.setCreatedBy(actor.accountId());
            row.setCreatedByName(actor.name());
            permitProjectNatureRepository.save(row);
        }
    }

    private void validateScopeTags(UUID companyId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<ApprovalScopeTag> found = scopeTagRepository.findByCompanyIdAndDeletedFalseAndIdIn(companyId, ids);
        if (found.size() != ids.size()) {
            throw new NotFoundException("One or more scope tags were not found");
        }
    }

    private void validatePermits(UUID companyId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<ApprovalPermitType> found = permitTypeRepository.findByCompanyIdAndDeletedFalseAndIdIn(companyId, ids);
        if (found.size() != ids.size()) {
            throw new NotFoundException("One or more permit types were not found");
        }
    }

    private void validatePropertyTypes(UUID companyId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<ApprovalPropertyType> found = propertyTypeRepository.findByCompanyIdAndDeletedFalseAndIdIn(companyId, ids);
        if (found.size() != ids.size()) {
            throw new NotFoundException("One or more property types were not found");
        }
    }

    private void validateProjectNatures(UUID companyId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<ApprovalProjectNature> found = projectNatureRepository.findByCompanyIdAndDeletedFalseAndIdIn(companyId, ids);
        if (found.size() != ids.size()) {
            throw new NotFoundException("One or more project natures were not found");
        }
    }

    private CatalogLinks loadLinks(UUID companyId) {
        List<ApprovalPermitType> permits = permitTypeRepository.findByCompanyIdAndDeletedFalseOrderByPermitCodeAsc(companyId);
        List<ApprovalScopeTag> tags = scopeTagRepository.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId);
        Map<UUID, ApprovalPermitType> permitMap = permits.stream()
                .collect(Collectors.toMap(ApprovalPermitType::getId, p -> p));
        Map<UUID, ApprovalScopeTag> tagMap = tags.stream()
                .collect(Collectors.toMap(ApprovalScopeTag::getId, t -> t));
        Map<UUID, List<ApprovalPermitScopeTag>> byPermit = new HashMap<>();
        Map<UUID, List<ApprovalPermitScopeTag>> byTag = new HashMap<>();
        for (ApprovalPermitScopeTag row : permitScopeTagRepository.findByCompanyId(companyId)) {
            byPermit.computeIfAbsent(row.getPermitTypeId(), k -> new ArrayList<>()).add(row);
            byTag.computeIfAbsent(row.getScopeTagId(), k -> new ArrayList<>()).add(row);
        }
        Map<UUID, ApprovalPropertyType> propertyMap = propertyTypeRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalPropertyType::getId, t -> t));
        Map<UUID, ApprovalProjectNature> natureMap = projectNatureRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalProjectNature::getId, t -> t));
        Map<UUID, List<ApprovalPermitPropertyType>> propertyByPermit = new HashMap<>();
        Map<UUID, List<ApprovalPermitPropertyType>> propertyByItem = new HashMap<>();
        for (ApprovalPermitPropertyType row : permitPropertyTypeRepository.findByCompanyId(companyId)) {
            propertyByPermit.computeIfAbsent(row.getPermitTypeId(), k -> new ArrayList<>()).add(row);
            propertyByItem.computeIfAbsent(row.getPropertyTypeId(), k -> new ArrayList<>()).add(row);
        }
        Map<UUID, List<ApprovalPermitProjectNature>> natureByPermit = new HashMap<>();
        Map<UUID, List<ApprovalPermitProjectNature>> natureByItem = new HashMap<>();
        for (ApprovalPermitProjectNature row : permitProjectNatureRepository.findByCompanyId(companyId)) {
            natureByPermit.computeIfAbsent(row.getPermitTypeId(), k -> new ArrayList<>()).add(row);
            natureByItem.computeIfAbsent(row.getProjectNatureId(), k -> new ArrayList<>()).add(row);
        }
        return new CatalogLinks(permitMap, tagMap, byPermit, byTag,
                propertyMap, natureMap, propertyByPermit, propertyByItem, natureByPermit, natureByItem);
    }

    private Actor currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            String name = StringUtils.hasText(principal.getFullName())
                    ? principal.getFullName()
                    : principal.getEmail();
            return new Actor(principal.getAccountId(), name);
        }
        return new Actor(null, null);
    }

    private record CatalogLinks(
            Map<UUID, ApprovalPermitType> permits,
            Map<UUID, ApprovalScopeTag> tags,
            Map<UUID, List<ApprovalPermitScopeTag>> byPermit,
            Map<UUID, List<ApprovalPermitScopeTag>> byTag,
            Map<UUID, ApprovalPropertyType> propertyTypes,
            Map<UUID, ApprovalProjectNature> projectNatures,
            Map<UUID, List<ApprovalPermitPropertyType>> propertyByPermit,
            Map<UUID, List<ApprovalPermitPropertyType>> propertyByItem,
            Map<UUID, List<ApprovalPermitProjectNature>> natureByPermit,
            Map<UUID, List<ApprovalPermitProjectNature>> natureByItem) {
    }

    private record Actor(Long accountId, String name) {
    }

    private CatalogItemResponse toPropertyType(ApprovalPropertyType entity, CatalogLinks links) {
        return toCatalogItem(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.isActive(), entity.getUpdatedAt(),
                links.propertyByItem.getOrDefault(entity.getId(), List.of()).stream()
                        .map(row -> triggeredPermit(links, row.getPermitTypeId(), row.getCreatedByName(), row.getCreatedAt()))
                        .filter(item -> item != null)
                        .sorted(Comparator.comparing(LinkedPermitTypeResponse::getPermitCode, String.CASE_INSENSITIVE_ORDER))
                        .toList());
    }

    private CatalogItemResponse toProjectNature(ApprovalProjectNature entity, CatalogLinks links) {
        return toCatalogItem(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.isActive(), entity.getUpdatedAt(),
                links.natureByItem.getOrDefault(entity.getId(), List.of()).stream()
                        .map(row -> triggeredPermit(links, row.getPermitTypeId(), row.getCreatedByName(), row.getCreatedAt()))
                        .filter(item -> item != null)
                        .sorted(Comparator.comparing(LinkedPermitTypeResponse::getPermitCode, String.CASE_INSENSITIVE_ORDER))
                        .toList());
    }

    private CatalogItemResponse toCatalogItem(UUID id, String code, String name, String description,
            boolean active, java.time.LocalDateTime updatedAt, List<LinkedPermitTypeResponse> triggered) {
        return CatalogItemResponse.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(description)
                .active(active)
                .updatedAt(updatedAt)
                .permitTypeIds(triggered.stream().map(LinkedPermitTypeResponse::getId).toList())
                .triggeredPermits(triggered)
                .build();
    }

    private LinkedPermitTypeResponse triggeredPermit(CatalogLinks links, UUID permitId, String createdByName,
            java.time.LocalDateTime createdAt) {
        ApprovalPermitType permit = links.permits.get(permitId);
        if (permit == null || permit.isDeleted()) {
            return null;
        }
        return LinkedPermitTypeResponse.builder()
                .id(permit.getId())
                .permitCode(permit.getPermitCode())
                .name(permit.getName())
                .createdByName(createdByName)
                .createdAt(createdAt)
                .build();
    }

    private void applyPropertyType(ApprovalPropertyType entity, CatalogItemRequest request, boolean creating) {
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

    private void applyProjectNature(ApprovalProjectNature entity, CatalogItemRequest request, boolean creating) {
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

    private ApprovalPropertyType propertyType(UUID id) {
        ApprovalPropertyType entity = propertyTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Property type not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private ApprovalProjectNature projectNature(UUID id) {
        ApprovalProjectNature entity = projectNatureRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Project nature not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private ApprovalCompanyRegistration companyRegistration(UUID id) {
        ApprovalCompanyRegistration entity = companyRegistrationRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Company registration not found"));
        assertCompany(entity.getCompanyId());
        return entity;
    }

    private List<CompanyRegistrationResponse> listCompanyRegistrations(UUID companyId) {
        Map<UUID, ApprovalAuthority> authorities = authorityRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(ApprovalAuthority::getId, a -> a));
        return companyRegistrationRepository.findByCompanyIdAndDeletedFalseOrderByUpdatedAtDesc(companyId).stream()
                .map(row -> toRegistration(row, authorities.get(row.getAuthorityId())))
                .toList();
    }

    private void applyRegistration(ApprovalCompanyRegistration entity, CompanyRegistrationRequest request) {
        if (request.getReferenceNo() != null) {
            entity.setReferenceNo(request.getReferenceNo());
        }
        entity.setRegistrationDate(request.getRegistrationDate());
        entity.setRenewalDate(request.getRenewalDate());
        if (StringUtils.hasText(request.getStatus())) {
            entity.setStatus(request.getStatus().trim());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
    }

    private CompanyRegistrationResponse toRegistration(ApprovalCompanyRegistration entity, ApprovalAuthority authority) {
        return CompanyRegistrationResponse.builder()
                .id(entity.getId())
                .authorityId(entity.getAuthorityId())
                .authorityCode(authority != null ? authority.getCode() : null)
                .authorityName(authority != null ? authority.getName() : null)
                .referenceNo(entity.getReferenceNo())
                .registrationDate(entity.getRegistrationDate())
                .renewalDate(entity.getRenewalDate())
                .status(entity.getStatus())
                .notes(entity.getNotes())
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
