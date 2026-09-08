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
import com.fitouts.approvalconfig.domain.ApprovalDocumentType;
import com.fitouts.approvalconfig.domain.ApprovalDocumentTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalPermitScopeTagRepository;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.ApprovalPermitTypeRepository;
import com.fitouts.approvalconfig.domain.ApprovalScopeTag;
import com.fitouts.approvalconfig.domain.ApprovalScopeTagRepository;
import com.fitouts.auth.security.AuthPrincipal;
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
    private final ApprovalPermitScopeTagRepository permitScopeTagRepository;
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
        replaceScopeTagsForPermit(entity, request.getScopeTagIds());
        return toPermit(entity, loadLinks(companyId));
    }

    @Transactional
    public PermitTypeResponse updatePermitType(UUID id, PermitTypeRequest request) {
        ApprovalPermitType entity = permit(id);
        applyPermit(entity, request, false);
        entity = permitTypeRepository.save(entity);
        replaceScopeTagsForPermit(entity, request.getScopeTagIds());
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
                .scopeTagIds(scopeTags.stream().map(LinkedScopeTagResponse::getId).toList())
                .scopeTags(scopeTags)
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
        return new CatalogLinks(permitMap, tagMap, byPermit, byTag);
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
            Map<UUID, List<ApprovalPermitScopeTag>> byTag) {
    }

    private record Actor(Long accountId, String name) {
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
