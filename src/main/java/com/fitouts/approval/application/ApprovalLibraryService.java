package com.fitouts.approval.application;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.approval.api.AuthorityRequest;
import com.fitouts.approval.api.AuthorityResponse;
import com.fitouts.approval.api.CompanyComplianceRequest;
import com.fitouts.approval.api.CompanyComplianceResponse;
import com.fitouts.approval.api.DocumentTypeResponse;
import com.fitouts.approval.api.JurisdictionRequest;
import com.fitouts.approval.api.JurisdictionResponse;
import com.fitouts.approval.api.PermitTypeRequest;
import com.fitouts.approval.api.PermitTypeResponse;
import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityRepository;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.CompanyCompliance;
import com.fitouts.approval.domain.CompanyComplianceRepository;
import com.fitouts.approval.domain.DocumentType;
import com.fitouts.approval.domain.DocumentTypeRepository;
import com.fitouts.approval.domain.Jurisdiction;
import com.fitouts.approval.domain.JurisdictionRepository;
import com.fitouts.approval.domain.PermitType;
import com.fitouts.approval.domain.PermitTypeRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Read and maintain the approval reference library: authorities, jurisdictions, permit types
 * and document types, plus the company's own compliance documents.
 *
 * <p>Seed rows are global (null company) and read-only to tenants. A tenant that needs a
 * community or building the seed does not cover creates its own row, which then takes
 * precedence in lookups.
 */
@Service
@RequiredArgsConstructor
public class ApprovalLibraryService {

    private final AuthorityRepository authorityRepository;
    private final JurisdictionRepository jurisdictionRepository;
    private final PermitTypeRepository permitTypeRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final CompanyComplianceRepository complianceRepository;

    // Authorities

    @Transactional(readOnly = true)
    public List<AuthorityResponse> listAuthorities(String emirate, String type, String search) {
        UUID companyId = requireCompany();
        return authorityRepository.findVisible(companyId).stream()
                .filter(a -> emirate == null || emirate.isBlank() || emirate.equalsIgnoreCase(a.getEmirate()))
                .filter(a -> type == null || type.isBlank() || type.equalsIgnoreCase(a.getType().name()))
                .filter(a -> matchesSearch(search, a.getCode(), a.getName(), a.getJurisdictionAreas()))
                .map(this::toAuthority)
                .toList();
    }

    @Transactional
    public AuthorityResponse createAuthority(AuthorityRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        if (request == null || !StringUtils.hasText(request.getCode()) || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("code and name are required");
        }
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        boolean clashesWithTenantRow = authorityRepository.findByCodeVisible(code, companyId).stream()
                .anyMatch(a -> companyId.equals(a.getCompanyId()));
        if (clashesWithTenantRow) {
            throw new BadRequestException("An authority with code " + code + " already exists");
        }

        Authority authority = new Authority();
        authority.setCompanyId(companyId);
        authority.setCode(code);
        applyAuthority(authority, request);
        return toAuthority(authorityRepository.save(authority));
    }

    @Transactional
    public AuthorityResponse updateAuthority(UUID uuid, AuthorityRequest request) {
        requireAdmin();
        Authority authority = requireTenantOwned(authorityRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Authority not found")));
        applyAuthority(authority, request);
        return toAuthority(authorityRepository.save(authority));
    }

    // Jurisdictions

    @Transactional(readOnly = true)
    public List<JurisdictionResponse> listJurisdictions(String emirate, String search, Boolean verifiedOnly) {
        UUID companyId = requireCompany();
        Map<String, String> authorityNames = authorityNameIndex(companyId);
        return jurisdictionRepository.findVisible(companyId).stream()
                .filter(j -> emirate == null || emirate.isBlank() || emirate.equalsIgnoreCase(j.getEmirate()))
                .filter(j -> verifiedOnly == null || !verifiedOnly || j.isVerified())
                .filter(j -> matchesSearch(search, j.getCommunityName(), j.getBuildingName(), j.getAliases()))
                .map(j -> toJurisdiction(j, authorityNames))
                .toList();
    }

    @Transactional
    public JurisdictionResponse createJurisdiction(JurisdictionRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        if (request == null || !StringUtils.hasText(request.getCommunityName())) {
            throw new BadRequestException("communityName is required");
        }
        Jurisdiction row = new Jurisdiction();
        row.setCompanyId(companyId);
        applyJurisdiction(row, request);
        // A tenant entering a community by hand has verified it by definition.
        row.setVerified(request.getVerified() == null || request.getVerified());
        row.setDerivedFrom("tenant");
        return toJurisdiction(jurisdictionRepository.save(row), authorityNameIndex(companyId));
    }

    @Transactional
    public JurisdictionResponse updateJurisdiction(UUID uuid, JurisdictionRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        Jurisdiction existing = jurisdictionRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Jurisdiction not found"));

        // Editing a derived global row copies it into the tenant rather than mutating shared seed data.
        Jurisdiction target = existing;
        if (existing.getCompanyId() == null) {
            target = new Jurisdiction();
            target.setCompanyId(companyId);
            target.setDerivedFrom(existing.getDerivedFrom());
            target.setEmirate(existing.getEmirate());
            target.setCommunityName(existing.getCommunityName());
            target.setCommunityKey(existing.getCommunityKey());
        }
        applyJurisdiction(target, request);
        if (request.getVerified() != null) {
            target.setVerified(request.getVerified());
        }
        return toJurisdiction(jurisdictionRepository.save(target), authorityNameIndex(companyId));
    }

    // Permit types

    @Transactional(readOnly = true)
    public List<PermitTypeResponse> listPermitTypes(String authorityCode, String search) {
        UUID companyId = requireCompany();
        Map<String, String> authorityNames = authorityNameIndex(companyId);
        return permitTypeRepository.findVisible(companyId).stream()
                .filter(p -> authorityCode == null || authorityCode.isBlank()
                        || authorityCode.equalsIgnoreCase(p.getAuthorityCode()))
                .filter(p -> matchesSearch(search, p.getCode(), p.getName(), p.getTypicalTrigger()))
                .map(p -> toPermitType(p, authorityNames))
                .toList();
    }

    @Transactional
    public PermitTypeResponse createPermitType(PermitTypeRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        if (request == null || !StringUtils.hasText(request.getCode()) || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("code and name are required");
        }
        PermitType permit = new PermitType();
        permit.setCompanyId(companyId);
        permit.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        applyPermitType(permit, request);
        return toPermitType(permitTypeRepository.save(permit), authorityNameIndex(companyId));
    }

    @Transactional
    public PermitTypeResponse updatePermitType(UUID uuid, PermitTypeRequest request) {
        requireAdmin();
        UUID companyId = requireCompany();
        PermitType existing = permitTypeRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Permit type not found"));

        PermitType target = existing;
        if (existing.getCompanyId() == null) {
            target = copyForTenant(existing, companyId);
        }
        applyPermitType(target, request);
        return toPermitType(permitTypeRepository.save(target), authorityNameIndex(companyId));
    }

    // Document types

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> listDocumentTypes(String category) {
        UUID companyId = requireCompany();
        return documentTypeRepository.findVisible(companyId).stream()
                .filter(d -> category == null || category.isBlank() || category.equalsIgnoreCase(d.getCategory()))
                .map(this::toDocumentType)
                .toList();
    }

    // Company compliance

    @Transactional(readOnly = true)
    public List<CompanyComplianceResponse> listCompanyCompliance() {
        UUID companyId = requireCompany();
        Map<String, DocumentType> docTypes = documentTypeIndex(companyId);

        // Show every expiry-tracked document type, including ones with nothing uploaded yet,
        // so a missing insurance certificate is visible rather than merely absent.
        Map<String, CompanyCompliance> held = new LinkedHashMap<>();
        for (CompanyCompliance row : complianceRepository.findByCompanyIdOrderByDocumentTypeCodeAsc(companyId)) {
            held.put(row.getDocumentTypeCode(), row);
        }

        List<CompanyComplianceResponse> out = new ArrayList<>();
        for (DocumentType type : docTypes.values()) {
            if (!type.isExpiryTracked() && !held.containsKey(type.getCode())) {
                continue;
            }
            CompanyCompliance row = held.remove(type.getCode());
            out.add(toCompliance(row, type));
        }
        for (CompanyCompliance orphan : held.values()) {
            out.add(toCompliance(orphan, docTypes.get(orphan.getDocumentTypeCode())));
        }
        return out;
    }

    @Transactional
    public CompanyComplianceResponse upsertCompanyCompliance(CompanyComplianceRequest request) {
        requireStaff();
        UUID companyId = requireCompany();
        if (request == null || !StringUtils.hasText(request.getDocumentTypeCode())) {
            throw new BadRequestException("documentTypeCode is required");
        }
        String code = request.getDocumentTypeCode().trim().toUpperCase(Locale.ROOT);
        CompanyCompliance row = complianceRepository.findByCompanyIdAndDocumentTypeCode(companyId, code)
                .orElseGet(() -> {
                    CompanyCompliance created = new CompanyCompliance();
                    created.setCompanyId(companyId);
                    created.setDocumentTypeCode(code);
                    return created;
                });

        row.setReferenceNo(SeedValueParser.trimToNull(request.getReferenceNo()));
        row.setIssueDate(request.getIssueDate());
        row.setExpiryDate(request.getExpiryDate());
        if (request.getFilePath() != null) {
            row.setFilePath(SeedValueParser.trimToNull(request.getFilePath()));
        }
        row.setRenewalOwnerAccountId(request.getRenewalOwnerAccountId());
        row.setNotes(request.getNotes());
        if ("NOT_APPLICABLE".equalsIgnoreCase(request.getStatus())) {
            row.setStatus("NOT_APPLICABLE");
        } else {
            row.setStatus("MISSING");
            row.refreshStatus();
        }

        CompanyCompliance saved = complianceRepository.save(row);
        return toCompliance(saved, documentTypeIndex(companyId).get(code));
    }

    /**
     * Company documents that are missing or expired. The company gate reads this before
     * letting a case be submitted in a community.
     */
    @Transactional(readOnly = true)
    public List<String> blockingComplianceCodes(UUID companyId, List<String> requiredCodes) {
        if (requiredCodes == null || requiredCodes.isEmpty()) return List.of();
        Map<String, CompanyCompliance> held = new LinkedHashMap<>();
        for (CompanyCompliance row : complianceRepository.findByCompanyIdOrderByDocumentTypeCodeAsc(companyId)) {
            held.put(row.getDocumentTypeCode(), row);
        }
        List<String> blocking = new ArrayList<>();
        for (String code : requiredCodes) {
            CompanyCompliance row = held.get(code);
            if (row == null) {
                blocking.add(code);
                continue;
            }
            row.refreshStatus();
            if ("MISSING".equals(row.getStatus()) || "EXPIRED".equals(row.getStatus())) {
                blocking.add(code);
            }
        }
        return blocking;
    }

    // Lookup helpers shared with the resolver and case services

    @Transactional(readOnly = true)
    public Map<String, Authority> authorityIndex(UUID companyId) {
        Map<String, Authority> index = new LinkedHashMap<>();
        for (Authority authority : authorityRepository.findVisible(companyId)) {
            // findVisible sorts global rows alongside tenant rows; tenant wins on a code clash.
            index.merge(authority.getCode(), authority,
                    (existing, candidate) -> candidate.getCompanyId() != null ? candidate : existing);
        }
        return index;
    }

    @Transactional(readOnly = true)
    public Map<String, DocumentType> documentTypeIndex(UUID companyId) {
        Map<String, DocumentType> index = new LinkedHashMap<>();
        for (DocumentType type : documentTypeRepository.findVisible(companyId)) {
            index.merge(type.getCode(), type,
                    (existing, candidate) -> candidate.getCompanyId() != null ? candidate : existing);
        }
        return index;
    }

    @Transactional(readOnly = true)
    public Map<String, PermitType> permitTypeIndex(UUID companyId) {
        Map<String, PermitType> index = new LinkedHashMap<>();
        for (PermitType type : permitTypeRepository.findVisible(companyId)) {
            index.merge(type.getCode(), type,
                    (existing, candidate) -> candidate.getCompanyId() != null ? candidate : existing);
        }
        return index;
    }

    // Mapping

    public AuthorityResponse toAuthority(Authority a) {
        return AuthorityResponse.builder()
                .uuid(a.getUuid())
                .code(a.getCode())
                .name(a.getName())
                .type(a.getType() != null ? a.getType().name() : null)
                .emirate(a.getEmirate())
                .jurisdictionAreas(a.getJurisdictionAreas())
                .permitsIssuedTypical(a.getPermitsIssuedTypical())
                .submissionChannel(a.getSubmissionChannel())
                .portalUrl(a.getPortalUrl())
                .notes(a.getNotes())
                .active(a.isActive())
                .verifiedBy(a.getVerifiedBy())
                .verifiedDate(a.getVerifiedDate())
                .sourceUrl(a.getSourceUrl())
                .tenantOwned(a.getCompanyId() != null)
                .build();
    }

    public PermitTypeResponse toPermitType(PermitType p, Map<String, String> authorityNames) {
        Integer planning = p.planningSlaDays();
        boolean usingActual = p.getActualMedianSlaDays() != null && p.getCompletedCaseCount() >= 10;
        return PermitTypeResponse.builder()
                .uuid(p.getUuid())
                .code(p.getCode())
                .name(p.getName())
                .authorityCode(p.getAuthorityCode())
                .authorityName(p.getAuthorityCode() != null ? authorityNames.get(p.getAuthorityCode()) : null)
                .authorityType(p.getAuthorityType())
                .typicalTrigger(p.getTypicalTrigger())
                .prerequisitePermitCodes(SeedValueParser.splitList(p.getPrerequisitePermitCodes()))
                .prerequisiteNotes(p.getPrerequisiteNotes())
                .indicativeSlaDaysMin(p.getIndicativeSlaDaysMin())
                .indicativeSlaDaysMax(p.getIndicativeSlaDaysMax())
                .indicativeSlaRaw(p.getIndicativeSlaRaw())
                .actualMedianSlaDays(p.getActualMedianSlaDays())
                .completedCaseCount(p.getCompletedCaseCount())
                .planningSlaDays(planning)
                .usingActualSla(usingActual)
                .validityDaysMin(p.getValidityDaysMin())
                .validityDaysMax(p.getValidityDaysMax())
                .validityRaw(p.getValidityRaw())
                .hasDeposit(p.isHasDeposit())
                .depositConditional(p.isDepositConditional())
                .depositAmountIndicative(p.getDepositAmountIndicative())
                .feeIndicative(p.getFeeIndicative())
                .renewable(p.isRenewable())
                .blocksActivitiesRaw(p.getBlocksActivitiesRaw())
                .blocksActivityCodes(SeedValueParser.splitList(p.getBlocksActivityCodes()))
                .requiredDocumentCodes(SeedValueParser.splitList(p.getRequiredDocumentCodes()))
                .active(p.isActive())
                .verifiedBy(p.getVerifiedBy())
                .verifiedDate(p.getVerifiedDate())
                .sourceUrl(p.getSourceUrl())
                .tenantOwned(p.getCompanyId() != null)
                .build();
    }

    public DocumentTypeResponse toDocumentType(DocumentType d) {
        return DocumentTypeResponse.builder()
                .uuid(d.getUuid())
                .code(d.getCode())
                .name(d.getName())
                .category(d.getCategory())
                .typicallyRequiredFor(d.getTypicallyRequiredFor())
                .expiryTracked(d.isExpiryTracked())
                .ownerRole(d.getOwnerRole())
                .sourceOwner(d.getSourceOwner())
                .active(d.isActive())
                .tenantOwned(d.getCompanyId() != null)
                .build();
    }

    public JurisdictionResponse toJurisdiction(Jurisdiction j, Map<String, String> authorityNames) {
        return JurisdictionResponse.builder()
                .uuid(j.getUuid())
                .emirate(j.getEmirate())
                .communityName(j.getCommunityName())
                .communityKey(j.getCommunityKey())
                .buildingName(j.getBuildingName())
                .plotZone(j.getPlotZone())
                .regulatorAuthorityCode(j.getRegulatorAuthorityCode())
                .regulatorAuthorityName(authorityNames.get(j.getRegulatorAuthorityCode()))
                .masterDeveloperAuthorityCode(j.getMasterDeveloperAuthorityCode())
                .masterDeveloperAuthorityName(authorityNames.get(j.getMasterDeveloperAuthorityCode()))
                .buildingManagementAuthorityCode(j.getBuildingManagementAuthorityCode())
                .utilityAuthorityCode(j.getUtilityAuthorityCode())
                .additionalAuthorityCodes(SeedValueParser.splitList(j.getAdditionalAuthorityCodes()))
                .verified(j.isVerified())
                .derivedFrom(j.getDerivedFrom())
                .notes(j.getNotes())
                .tenantOwned(j.getCompanyId() != null)
                .build();
    }

    private CompanyComplianceResponse toCompliance(CompanyCompliance row, DocumentType type) {
        if (row == null) {
            return CompanyComplianceResponse.builder()
                    .documentTypeCode(type.getCode())
                    .documentTypeName(type.getName())
                    .category(type.getCategory())
                    .status("MISSING")
                    .build();
        }
        row.refreshStatus();
        Long daysToExpiry = row.getExpiryDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), row.getExpiryDate())
                : null;
        return CompanyComplianceResponse.builder()
                .uuid(row.getUuid())
                .documentTypeCode(row.getDocumentTypeCode())
                .documentTypeName(type != null ? type.getName() : row.getDocumentTypeCode())
                .category(type != null ? type.getCategory() : null)
                .referenceNo(row.getReferenceNo())
                .issueDate(row.getIssueDate())
                .expiryDate(row.getExpiryDate())
                .filePath(row.getFilePath())
                .status(row.getStatus())
                .renewalOwnerAccountId(row.getRenewalOwnerAccountId())
                .notes(row.getNotes())
                .daysToExpiry(daysToExpiry)
                .build();
    }

    // Internals

    private Map<String, String> authorityNameIndex(UUID companyId) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Authority authority : authorityRepository.findVisible(companyId)) {
            names.putIfAbsent(authority.getCode(), authority.getName());
        }
        return names;
    }

    private void applyAuthority(Authority authority, AuthorityRequest request) {
        if (request.getName() != null) authority.setName(request.getName().trim());
        if (request.getType() != null) authority.setType(AuthorityType.fromLabel(request.getType()));
        if (authority.getType() == null) authority.setType(AuthorityType.SPECIAL);
        authority.setEmirate(SeedValueParser.trimToNull(request.getEmirate()));
        authority.setJurisdictionAreas(SeedValueParser.trimToNull(request.getJurisdictionAreas()));
        authority.setPermitsIssuedTypical(SeedValueParser.trimToNull(request.getPermitsIssuedTypical()));
        authority.setSubmissionChannel(SeedValueParser.trimToNull(request.getSubmissionChannel()));
        authority.setPortalUrl(SeedValueParser.trimToNull(request.getPortalUrl()));
        authority.setNotes(request.getNotes());
        if (request.getActive() != null) authority.setActive(request.getActive());
        authority.setVerifiedBy(SeedValueParser.trimToNull(request.getVerifiedBy()));
        authority.setVerifiedDate(request.getVerifiedDate());
        authority.setSourceUrl(SeedValueParser.trimToNull(request.getSourceUrl()));
    }

    private void applyJurisdiction(Jurisdiction row, JurisdictionRequest request) {
        if (request.getEmirate() != null) row.setEmirate(request.getEmirate().trim());
        if (row.getEmirate() == null) row.setEmirate("Dubai");
        if (request.getCommunityName() != null) {
            row.setCommunityName(request.getCommunityName().trim());
            row.setCommunityKey(SeedValueParser.communityKey(request.getCommunityName()));
        }
        row.setBuildingName(SeedValueParser.trimToNull(request.getBuildingName()));
        row.setPlotZone(SeedValueParser.trimToNull(request.getPlotZone()));
        row.setRegulatorAuthorityCode(SeedValueParser.trimToNull(request.getRegulatorAuthorityCode()));
        row.setMasterDeveloperAuthorityCode(SeedValueParser.trimToNull(request.getMasterDeveloperAuthorityCode()));
        row.setBuildingManagementAuthorityCode(SeedValueParser.trimToNull(request.getBuildingManagementAuthorityCode()));
        row.setUtilityAuthorityCode(SeedValueParser.trimToNull(request.getUtilityAuthorityCode()));
        row.setAdditionalAuthorityCodes(SeedValueParser.joinList(request.getAdditionalAuthorityCodes()));
        row.setNotes(request.getNotes());
    }

    private void applyPermitType(PermitType permit, PermitTypeRequest request) {
        if (request.getName() != null) permit.setName(request.getName().trim());
        permit.setAuthorityCode(SeedValueParser.trimToNull(request.getAuthorityCode()));
        permit.setAuthorityType(SeedValueParser.trimToNull(request.getAuthorityType()));
        permit.setTypicalTrigger(request.getTypicalTrigger());
        permit.setPrerequisitePermitCodes(SeedValueParser.joinList(request.getPrerequisitePermitCodes()));
        permit.setPrerequisiteNotes(request.getPrerequisiteNotes());
        permit.setIndicativeSlaDaysMin(request.getIndicativeSlaDaysMin());
        permit.setIndicativeSlaDaysMax(request.getIndicativeSlaDaysMax());
        permit.setValidityDaysMin(request.getValidityDaysMin());
        permit.setValidityDaysMax(request.getValidityDaysMax());
        if (request.getHasDeposit() != null) permit.setHasDeposit(request.getHasDeposit());
        if (request.getDepositConditional() != null) permit.setDepositConditional(request.getDepositConditional());
        permit.setDepositAmountIndicative(request.getDepositAmountIndicative());
        permit.setFeeIndicative(request.getFeeIndicative());
        if (request.getRenewable() != null) permit.setRenewable(request.getRenewable());
        permit.setBlocksActivityCodes(SeedValueParser.joinList(request.getBlocksActivityCodes()));
        permit.setRequiredDocumentCodes(SeedValueParser.joinList(request.getRequiredDocumentCodes()));
        if (request.getActive() != null) permit.setActive(request.getActive());
        permit.setVerifiedBy(SeedValueParser.trimToNull(request.getVerifiedBy()));
        permit.setVerifiedDate(request.getVerifiedDate());
        permit.setSourceUrl(SeedValueParser.trimToNull(request.getSourceUrl()));
    }

    private PermitType copyForTenant(PermitType source, UUID companyId) {
        PermitType copy = new PermitType();
        copy.setCompanyId(companyId);
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setAuthorityCode(source.getAuthorityCode());
        copy.setAuthorityType(source.getAuthorityType());
        copy.setTypicalTrigger(source.getTypicalTrigger());
        copy.setPrerequisitePermitCodes(source.getPrerequisitePermitCodes());
        copy.setPrerequisiteNotes(source.getPrerequisiteNotes());
        copy.setIndicativeSlaRaw(source.getIndicativeSlaRaw());
        copy.setValidityRaw(source.getValidityRaw());
        copy.setBlocksActivitiesRaw(source.getBlocksActivitiesRaw());
        return copy;
    }

    private Authority requireTenantOwned(Authority authority) {
        UUID companyId = requireCompany();
        if (authority.getCompanyId() == null) {
            throw new BadRequestException("Seed authorities are shared and cannot be edited. Add a tenant authority instead.");
        }
        if (!companyId.equals(authority.getCompanyId())) {
            throw new ForbiddenException("Authority not in your company");
        }
        return authority;
    }

    private boolean matchesSearch(String search, String... fields) {
        if (search == null || search.isBlank()) return true;
        String needle = search.trim().toLowerCase(Locale.ROOT);
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requireAdmin() {
        AuthPrincipal principal = requireStaff();
        if (principal.getRoles() == null || !(
                principal.getRoles().contains(Role.ADMIN)
                        || principal.getRoles().contains(Role.SUPER_ADMIN)
                        || principal.getRoles().contains(Role.BUSINESS_OWNER)
                        || principal.getRoles().contains(Role.PROJECT_MANAGER))) {
            throw new ForbiddenException("Admin access required");
        }
        return principal;
    }
}
