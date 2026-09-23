package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.application.ClientAccountConversionResult;
import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.company.application.CompanyService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScComplianceDocumentResponse;
import com.fitouts.subcontractor.api.ScInviteVendorRequest;
import com.fitouts.subcontractor.api.ScOrganizationExtensionResponse;
import com.fitouts.subcontractor.api.ScPrequalificationReviewRequest;
import com.fitouts.subcontractor.api.ScPublicRegistrationInfoResponse;
import com.fitouts.subcontractor.api.ScPublicRegistrationLinkResponse;
import com.fitouts.subcontractor.api.ScPublicRegistrationRequest;
import com.fitouts.subcontractor.api.ScTradeDecisionRequest;
import com.fitouts.subcontractor.api.ScVendorDetailResponse;
import com.fitouts.subcontractor.api.ScVendorSummaryResponse;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScCompanyStatus;
import com.fitouts.subcontractor.domain.ScComplianceDocType;
import com.fitouts.subcontractor.domain.ScComplianceDocument;
import com.fitouts.subcontractor.domain.ScComplianceDocumentRepository;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScPortalUserStatus;
import com.fitouts.subcontractor.domain.ScRegistrationInvite;
import com.fitouts.subcontractor.domain.ScRegistrationInviteRepository;
import com.fitouts.subcontractor.domain.ScTenantMembership;
import com.fitouts.subcontractor.domain.ScTenantMembershipRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScVendorService {

    private static final int INVITE_VALID_DAYS = 7;
    private static final int PUBLIC_LINK_VALID_DAYS = 365;

    /** Sentinel email for tenant-wide public self-registration links (not a real mailbox). */
    static final String PUBLIC_REGISTRATION_EMAIL = "__PUBLIC_REGISTRATION__";

    /** Demo / local invites use the same password as V15 seeded users — only for @fitouts.demo. */
    private static final String DEMO_INVITE_PASSWORD = "123456";
    private static final String DEMO_EMAIL_DOMAIN = "@fitouts.demo";

    private static final Set<Role> INVITE_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private static final Set<Role> PREQUAL_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private final ScOrganizationRepository organizationRepository;
    private final ScTenantMembershipRepository membershipRepository;
    private final ScPortalUserRepository portalUserRepository;
    private final ScCompanyProfileRepository profileRepository;
    private final ScComplianceDocumentRepository complianceRepository;
    private final ScRegistrationInviteRepository inviteRepository;
    private final AccountService accountService;
    private final ClientPortalInviteService portalInviteService;
    private final ScOrganizationResolver organizationResolver;
    private final ScEligibilityService eligibilityService;
    private final ScOrganizationProfileService organizationProfileService;
    private final CompanyService companyService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScVendorSummaryResponse inviteVendor(ScInviteVendorRequest request) {
        AuthPrincipal principal = requireRoles(INVITE_ROLES);
        UUID tenantId = requireCompany();
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            throw new BadRequestException("Subcontractor email is required");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        ClientAccountConversionResult accountResult = isDemoInviteEmail(normalizedEmail)
                ? accountService.createOrUpdateSubcontractorAccountWithPassword(
                        request.getFullName(),
                        normalizedEmail,
                        request.getPhone(),
                        request.getCompanyName(),
                        DEMO_INVITE_PASSWORD)
                : accountService.createOrUpdateSubcontractorAccount(
                        request.getFullName(),
                        normalizedEmail,
                        request.getPhone(),
                        request.getCompanyName());

        ScOrganization org = organizationResolver.bootstrapForAccount(accountResult.clientAccountId(), tenantId);

        if (StringUtils.hasText(request.getCompanyName())) {
            org.setLegalCompanyName(request.getCompanyName().trim());
        }
        if (StringUtils.hasText(request.getFullName())) {
            org.setPrimaryContactName(request.getFullName().trim());
        }
        org.setPrimaryContactEmail(normalizedEmail);
        if (StringUtils.hasText(request.getPhone())) {
            org.setPrimaryContactPhone(request.getPhone().trim());
        }
        organizationRepository.save(org);

        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(org.getUuid(), tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant membership not found"));
        membership.setStatus(ScCompanyStatus.INVITED);
        membershipRepository.save(membership);

        ScRegistrationInvite invite = new ScRegistrationInvite();
        invite.setCompanyId(tenantId);
        invite.setEmail(normalizedEmail);
        invite.setOrganizationUuid(org.getUuid());
        invite.setExpiresAt(OffsetDateTime.now().plus(INVITE_VALID_DAYS, ChronoUnit.DAYS));
        invite.setCreatedByAccountId(principal.getAccountId());
        inviteRepository.save(invite);

        String inviteName = org.getLegalCompanyName() != null
                ? org.getLegalCompanyName()
                : (StringUtils.hasText(request.getFullName()) ? request.getFullName().trim() : "there");
        portalInviteService.sendSubcontractorPortalInvite(accountResult.clientAccountId(), inviteName);

        return toSummary(org, membership);
    }

    @Transactional(readOnly = true)
    public List<ScVendorSummaryResponse> listVendors(String statusFilter) {
        requireRoles(PREQUAL_ROLES);
        UUID tenantId = requireCompany();
        return membershipRepository.findByCompanyIdOrderByUpdatedAtDesc(tenantId).stream()
                .filter(m -> statusFilter == null || statusFilter.isBlank()
                        || m.getStatus().name().equalsIgnoreCase(statusFilter.trim()))
                .map(m -> {
                    ScOrganization org = organizationRepository.findById(m.getOrganizationUuid()).orElse(null);
                    if (org == null) {
                        return null;
                    }
                    return toSummary(org, m);
                })
                .filter(v -> v != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScVendorDetailResponse getVendor(UUID organizationUuid) {
        AuthPrincipal principal = requireRoles(PREQUAL_ROLES);
        UUID tenantId = requireCompany();
        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(organizationUuid, tenantId)
                .orElseThrow(() -> new NotFoundException("Subcontractor vendor not found"));
        ScOrganization org = organizationRepository.findById(organizationUuid)
                .orElseThrow(() -> new NotFoundException("Subcontractor organization not found"));
        return toDetail(org, membership, principal);
    }

    @Transactional
    public ScVendorSummaryResponse reviewPrequalification(UUID organizationUuid, ScPrequalificationReviewRequest request) {
        AuthPrincipal principal = requireRoles(PREQUAL_ROLES);
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new BadRequestException("Review status is required");
        }

        UUID tenantId = requireCompany();
        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(organizationUuid, tenantId)
                .orElseThrow(() -> new NotFoundException("Subcontractor vendor not found"));

        ScCompanyStatus newStatus = parseStatus(request.getStatus());
        if (newStatus == ScCompanyStatus.INVITED || newStatus == ScCompanyStatus.REGISTERED) {
            throw new BadRequestException("Use APPROVED, CONDITIONAL, SUSPENDED, BLACKLISTED, or UNDER_REVIEW");
        }

        membership.setStatus(newStatus);
        if (request.getNotes() != null) {
            membership.setPrequalificationNotes(trimToNull(request.getNotes()));
        }

        List<String> approved = request.getApprovedTrades();
        if (request.getTradeDecisions() != null && !request.getTradeDecisions().isEmpty()) {
            List<String> approvedFromDecisions = new ArrayList<>();
            List<Map<String, String>> rejected = new ArrayList<>();
            for (ScTradeDecisionRequest decision : request.getTradeDecisions()) {
                if (decision == null || !StringUtils.hasText(decision.getTrade())) {
                    continue;
                }
                String trade = decision.getTrade().trim();
                String action = decision.getDecision() == null ? "" : decision.getDecision().trim().toUpperCase();
                if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
                    approvedFromDecisions.add(trade);
                } else if ("REJECT".equals(action) || "REJECTED".equals(action)) {
                    if (!StringUtils.hasText(decision.getReason())) {
                        throw new BadRequestException("Rejection reason is required for trade: " + trade);
                    }
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("trade", trade);
                    row.put("reason", decision.getReason().trim());
                    rejected.add(row);
                } else {
                    throw new BadRequestException("Trade decision must be APPROVE or REJECT for: " + trade);
                }
            }
            approved = approvedFromDecisions;
            try {
                Map<String, Object> notesPayload = new LinkedHashMap<>();
                if (StringUtils.hasText(request.getNotes())) {
                    notesPayload.put("summary", request.getNotes().trim());
                }
                notesPayload.put("rejectedTrades", rejected);
                membership.setPrequalificationNotes(objectMapper.writeValueAsString(notesPayload));
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Could not store trade review notes");
            }
        }

        if (approved != null) {
            membership.setApprovedTrades(writeJson(approved));
        }
        if (request.getMaxPackageValue() != null) {
            membership.setMaxPackageValue(request.getMaxPackageValue());
        }
        membership.setReviewedByAccountId(principal.getAccountId());
        membership.setReviewedAt(OffsetDateTime.now());
        membershipRepository.save(membership);

        syncLegacyProfileStatus(organizationUuid, tenantId, newStatus);

        ScOrganization org = organizationRepository.findById(organizationUuid)
                .orElseThrow(() -> new NotFoundException("Subcontractor organization not found"));
        return toSummary(org, membership);
    }

    @Transactional
    public ScPublicRegistrationLinkResponse getOrCreatePublicRegistrationLink(boolean regenerate) {
        AuthPrincipal principal = requireRoles(INVITE_ROLES);
        UUID tenantId = requireCompany();
        OffsetDateTime now = OffsetDateTime.now();

        if (regenerate) {
            inviteRepository.findFirstByCompanyIdAndEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                    tenantId, PUBLIC_REGISTRATION_EMAIL, now)
                    .ifPresent(existing -> {
                        existing.setUsedAt(now);
                        inviteRepository.save(existing);
                    });
        } else {
            Optional<ScRegistrationInvite> existing = inviteRepository
                    .findFirstByCompanyIdAndEmailAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                            tenantId, PUBLIC_REGISTRATION_EMAIL, now);
            if (existing.isPresent()) {
                return toPublicLinkResponse(existing.get(), false);
            }
        }

        ScRegistrationInvite invite = new ScRegistrationInvite();
        invite.setCompanyId(tenantId);
        invite.setEmail(PUBLIC_REGISTRATION_EMAIL);
        invite.setOrganizationUuid(null);
        invite.setExpiresAt(now.plus(PUBLIC_LINK_VALID_DAYS, ChronoUnit.DAYS));
        invite.setCreatedByAccountId(principal.getAccountId());
        inviteRepository.save(invite);
        return toPublicLinkResponse(invite, regenerate);
    }

    @Transactional(readOnly = true)
    public ScPublicRegistrationInfoResponse getPublicRegistrationInfo(UUID token) {
        ScRegistrationInvite invite = inviteRepository.findById(token)
                .orElseThrow(() -> new NotFoundException("Registration link not found"));
        if (!PUBLIC_REGISTRATION_EMAIL.equals(invite.getEmail())) {
            throw new NotFoundException("Registration link not found");
        }
        String displayName = "Main contractor";
        try {
            displayName = companyService.getCompany(invite.getCompanyId()).getCompanyName();
        } catch (Exception ignored) {
            // Keep generic label — never expose internal IDs.
        }
        return ScPublicRegistrationInfoResponse.builder()
                .token(invite.getToken())
                .contractorDisplayName(displayName)
                .expiresAt(invite.getExpiresAt() != null ? invite.getExpiresAt().toString() : null)
                .usable(invite.isUsable())
                .build();
    }

    @Transactional
    public ScVendorSummaryResponse completePublicRegistration(UUID token, ScPublicRegistrationRequest request) {
        ScRegistrationInvite invite = inviteRepository.findById(token)
                .orElseThrow(() -> new NotFoundException("Registration link not found"));
        if (!PUBLIC_REGISTRATION_EMAIL.equals(invite.getEmail()) || !invite.isUsable()) {
            throw new BadRequestException("This registration link is no longer valid");
        }
        if (request == null || !StringUtils.hasText(request.getEmail()) || !StringUtils.hasText(request.getPassword())
                || !StringUtils.hasText(request.getCompanyName())) {
            throw new BadRequestException("Company name, email and password are required");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        UUID tenantId = invite.getCompanyId();
        CompanyContext.set(tenantId);
        try {
            ClientAccountConversionResult accountResult;
            if (isDemoInviteEmail(normalizedEmail) && "123456".equals(request.getPassword().trim())) {
                accountResult = accountService.createOrUpdateSubcontractorAccountWithPassword(
                        request.getContactName(),
                        normalizedEmail,
                        request.getPhone(),
                        request.getCompanyName(),
                        DEMO_INVITE_PASSWORD);
            } else {
                accountResult = accountService.createOrUpdateSubcontractorAccountWithPassword(
                        request.getContactName(),
                        normalizedEmail,
                        request.getPhone(),
                        request.getCompanyName(),
                        request.getPassword().trim());
            }

            ScOrganization org = organizationResolver.bootstrapForAccount(accountResult.clientAccountId(), tenantId);
            org.setLegalCompanyName(request.getCompanyName().trim());
            if (StringUtils.hasText(request.getContactName())) {
                org.setPrimaryContactName(request.getContactName().trim());
            }
            org.setPrimaryContactEmail(normalizedEmail);
            if (StringUtils.hasText(request.getPhone())) {
                org.setPrimaryContactPhone(request.getPhone().trim());
            }
            organizationRepository.save(org);

            ScTenantMembership membership = membershipRepository
                    .findByOrganizationUuidAndCompanyId(org.getUuid(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Tenant membership not found"));
            membership.setStatus(ScCompanyStatus.REGISTERED);
            membershipRepository.save(membership);
            syncLegacyProfileStatus(org.getUuid(), tenantId, ScCompanyStatus.REGISTERED);

            return toSummary(org, membership);
        } finally {
            CompanyContext.clear();
        }
    }

    private ScPublicRegistrationLinkResponse toPublicLinkResponse(ScRegistrationInvite invite, boolean regenerated) {
        return ScPublicRegistrationLinkResponse.builder()
                .token(invite.getToken())
                .path("/register/subcontractor?token=" + invite.getToken())
                .expiresAt(invite.getExpiresAt() != null ? invite.getExpiresAt().toString() : null)
                .regenerated(regenerated)
                .build();
    }

    @Transactional
    public ScVendorSummaryResponse submitForPrequalificationReview() {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        UUID tenantId = requireCompany();
        ScPortalUser portalUser = portalUserRepository.findByAccountId(principal.getAccountId())
                .orElseThrow(() -> new ForbiddenException("Subcontractor portal user required"));
        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(portalUser.getOrganizationUuid(), tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant membership not found"));

        ScOrganization org = organizationRepository.findById(portalUser.getOrganizationUuid())
                .orElseThrow(() -> new NotFoundException("Organization not found"));
        ScCompanyProfile profile = profileRepository
                .findByOrganizationUuidAndCompanyId(org.getUuid(), tenantId)
                .orElse(null);

        List<String> missing = new ArrayList<>();
        if (profile == null || !StringUtils.hasText(profile.getLegalCompanyName())) {
            missing.add("Company legal name");
        }
        if (profile == null || !StringUtils.hasText(profile.getTradeLicenceNumber())
                || profile.getTradeLicenceExpiry() == null
                || !StringUtils.hasText(profile.getTradeLicenceFilePath())) {
            missing.add("Trade licence (number, expiry and file)");
        }
        List<String> trades = profile != null ? readList(profile.getTradeCategories()) : List.of();
        if (trades.isEmpty()) {
            missing.add("At least one trade selection");
        }
        if (profile != null) {
            for (String gap : eligibilityService.listComplianceGaps(profile)) {
                missing.add(gap);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Cannot submit for review. Missing: " + String.join("; ", missing));
        }

        membership.setStatus(ScCompanyStatus.UNDER_REVIEW);
        membershipRepository.save(membership);
        syncLegacyProfileStatus(org.getUuid(), tenantId, ScCompanyStatus.UNDER_REVIEW);
        return toSummary(org, membership);
    }

    @Transactional
    public void markProfileSubmittedForReview(Long accountId) {
        UUID tenantId = requireCompany();
        ScPortalUser portalUser = portalUserRepository.findByAccountId(accountId)
                .orElse(null);
        if (portalUser == null) {
            return;
        }
        membershipRepository.findByOrganizationUuidAndCompanyId(portalUser.getOrganizationUuid(), tenantId)
                .ifPresent(m -> {
                    if (m.getStatus() == ScCompanyStatus.INVITED || m.getStatus() == ScCompanyStatus.REGISTERED) {
                        m.setStatus(ScCompanyStatus.UNDER_REVIEW);
                        membershipRepository.save(m);
                        syncLegacyProfileStatus(portalUser.getOrganizationUuid(), tenantId, ScCompanyStatus.UNDER_REVIEW);
                    }
                });
    }

    private void syncLegacyProfileStatus(UUID organizationUuid, UUID tenantId, ScCompanyStatus status) {
        profileRepository.findByOrganizationUuidAndCompanyId(organizationUuid, tenantId).ifPresent(p -> {
            p.setStatus(status);
            profileRepository.save(p);
        });
    }

    private ScVendorSummaryResponse toSummary(ScOrganization org, ScTenantMembership membership) {
        UUID tenantId = requireCompany();
        ScCompanyProfile profile = profileRepository
                .findByOrganizationUuidAndCompanyId(org.getUuid(), tenantId)
                .or(() -> profileRepository.findFirstByOrganizationUuidOrderByUpdatedAtDesc(org.getUuid()))
                .orElseGet(() -> wrapProfile(org, membership));
        hydrateProfileFromOrg(profile, org);
        String compliance = eligibilityService.computeComplianceStatus(profile).name();
        List<String> tradeCats = readList(profile.getTradeCategories());
        if (tradeCats.isEmpty()) {
            tradeCats = readList(org.getTradeCategories());
        }
        return ScVendorSummaryResponse.builder()
                .organizationUuid(org.getUuid())
                .legalCompanyName(firstNonBlank(profile.getLegalCompanyName(), org.getLegalCompanyName()))
                .primaryContactEmail(firstNonBlank(profile.getPrimaryContactEmail(), org.getPrimaryContactEmail()))
                .status(membership.getStatus().name())
                .complianceStatus(compliance)
                .complianceGaps(eligibilityService.listComplianceGaps(profile))
                .performanceScore(org.getPerformanceScore())
                .tradeCategories(tradeCats)
                .approvedTrades(readList(membership.getApprovedTrades()))
                .maxPackageValue(membership.getMaxPackageValue())
                .reviewedAt(membership.getReviewedAt() != null ? membership.getReviewedAt().toString() : null)
                .prequalificationNotes(membership.getPrequalificationNotes())
                .build();
    }

    private ScVendorDetailResponse toDetail(
            ScOrganization org, ScTenantMembership membership, AuthPrincipal principal) {
        UUID tenantId = requireCompany();
        ScCompanyProfile profile = profileRepository
                .findByOrganizationUuidAndCompanyId(org.getUuid(), tenantId)
                .or(() -> profileRepository.findFirstByOrganizationUuidOrderByUpdatedAtDesc(org.getUuid()))
                .orElseGet(() -> wrapProfile(org, membership));
        hydrateProfileFromOrg(profile, org);

        List<String> tradeCats = readList(profile.getTradeCategories());
        if (tradeCats.isEmpty()) {
            tradeCats = readList(org.getTradeCategories());
        }

        List<ScComplianceDocumentResponse> docs = List.of();
        if (profile.getUuid() != null) {
            List<ScComplianceDocument> stored =
                    complianceRepository.findByProfileUuidOrderByDocumentTypeAsc(profile.getUuid());
            docs = buildComplianceDocResponses(profile, stored, tradeCats);
        }

        ScOrganizationExtensionResponse extension = null;
        try {
            if (profile.getOrganizationUuid() != null || org.getUuid() != null) {
                if (profile.getOrganizationUuid() == null) {
                    profile.setOrganizationUuid(org.getUuid());
                }
                extension = organizationProfileService.getExtension(principal, profile);
            }
        } catch (Exception ignored) {
            // Extension is optional for review; core profile/docs still show.
        }

        return ScVendorDetailResponse.builder()
                .organizationUuid(org.getUuid())
                .profileUuid(profile.getUuid())
                .legalCompanyName(firstNonBlank(profile.getLegalCompanyName(), org.getLegalCompanyName()))
                .status(membership.getStatus().name())
                .complianceStatus(eligibilityService.computeComplianceStatus(profile).name())
                .complianceGaps(eligibilityService.listComplianceGaps(profile))
                .performanceScore(org.getPerformanceScore())
                .tradeCategories(tradeCats)
                .approvedTrades(readList(membership.getApprovedTrades()))
                .maxPackageValue(membership.getMaxPackageValue())
                .reviewedAt(membership.getReviewedAt() != null ? membership.getReviewedAt().toString() : null)
                .prequalificationNotes(membership.getPrequalificationNotes())
                .primaryContactName(firstNonBlank(profile.getPrimaryContactName(), org.getPrimaryContactName()))
                .primaryContactEmail(firstNonBlank(profile.getPrimaryContactEmail(), org.getPrimaryContactEmail()))
                .primaryContactPhone(firstNonBlank(profile.getPrimaryContactPhone(), org.getPrimaryContactPhone()))
                .accountsContactName(profile.getAccountsContactName())
                .accountsContactEmail(profile.getAccountsContactEmail())
                .accountsContactPhone(profile.getAccountsContactPhone())
                .trn(profile.getTrn())
                .registeredAddress(profile.getRegisteredAddress())
                .declaredCapacity(firstNonBlank(profile.getDeclaredCapacity(), org.getDeclaredCapacity()))
                .tradeLicenceNumber(profile.getTradeLicenceNumber())
                .tradeLicenceExpiry(profile.getTradeLicenceExpiry() != null
                        ? profile.getTradeLicenceExpiry().toString() : null)
                .tradeLicenceFilePath(profile.getTradeLicenceFilePath())
                .complianceDocuments(docs)
                .organizationExtension(extension)
                .build();
    }

    private List<ScComplianceDocumentResponse> buildComplianceDocResponses(
            ScCompanyProfile profile, List<ScComplianceDocument> stored, List<String> trades) {
        java.util.Set<ScComplianceDocType> specialist = specialistDocsForTrades(trades);
        List<ScComplianceDocumentResponse> out = new ArrayList<>();
        for (ScComplianceDocType type : ScComplianceDocType.values()) {
            ScComplianceDocument doc = stored.stream()
                    .filter(d -> d.getDocumentType() == type)
                    .findFirst()
                    .orElse(null);
            String applicability = documentApplicability(type, specialist, trades);
            boolean required = "MANDATORY".equals(applicability);
            ScComplianceDocument effective = doc != null ? doc : placeholderDoc(profile.getUuid(), type);
            out.add(ScComplianceDocumentResponse.builder()
                    .uuid(effective.getUuid())
                    .documentType(type.name())
                    .documentLabel(type.displayName())
                    .referenceNo(effective.getReferenceNo())
                    .expiryDate(effective.getExpiryDate() != null ? effective.getExpiryDate().toString() : null)
                    .filePath(effective.getFilePath())
                    .requiredForAppointment(required)
                    .applicability(applicability)
                    .itemStatus(ScEligibilityService.itemStatus(
                            effective.getExpiryDate(), effective.getFilePath(), required))
                    .build());
        }
        return out;
    }

    private static ScComplianceDocument placeholderDoc(UUID profileUuid, ScComplianceDocType type) {
        ScComplianceDocument doc = new ScComplianceDocument();
        doc.setProfileUuid(profileUuid);
        doc.setDocumentType(type);
        return doc;
    }

    private static java.util.Set<ScComplianceDocType> specialistDocsForTrades(List<String> trades) {
        java.util.Set<ScComplianceDocType> required = new java.util.HashSet<>();
        if (trades == null) {
            return required;
        }
        for (String trade : trades) {
            if (trade == null) {
                continue;
            }
            String lower = trade.toLowerCase();
            if (lower.contains("fire")) {
                required.add(ScComplianceDocType.CIVIL_DEFENCE);
            }
            if (lower.contains("security") || lower.contains("cctv") || lower.contains("low voltage")) {
                required.add(ScComplianceDocType.SIRA);
            }
            if (lower.contains("electrical")) {
                required.add(ScComplianceDocType.DEWA_ELECTRICAL);
            }
            ScEligibilityService.mapSpecialLicenceText(trade).ifPresent(required::add);
        }
        return required;
    }

    private static String documentApplicability(
            ScComplianceDocType type, java.util.Set<ScComplianceDocType> specialistRequired, List<String> trades) {
        if (type == ScComplianceDocType.CAR_INSURANCE
                || type == ScComplianceDocType.TPL_INSURANCE
                || type == ScComplianceDocType.WC_INSURANCE) {
            return "MANDATORY";
        }
        boolean specialistType = type == ScComplianceDocType.CIVIL_DEFENCE
                || type == ScComplianceDocType.SIRA
                || type == ScComplianceDocType.DEWA_ELECTRICAL;
        if (specialistType) {
            if (specialistRequired.contains(type)) {
                return "MANDATORY";
            }
            if (trades != null && !trades.isEmpty()) {
                return "NOT_APPLICABLE";
            }
            return "OPTIONAL";
        }
        return "OPTIONAL";
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
    }

    /** Prefer org trade-licence fields when the legacy profile row is still blank. */
    private void hydrateProfileFromOrg(ScCompanyProfile profile, ScOrganization org) {
        if (org == null || profile == null) {
            return;
        }
        if (!StringUtils.hasText(profile.getTradeLicenceNumber()) && StringUtils.hasText(org.getTradeLicenceNumber())) {
            profile.setTradeLicenceNumber(org.getTradeLicenceNumber());
        }
        if (!StringUtils.hasText(profile.getTradeLicenceFilePath()) && StringUtils.hasText(org.getTradeLicenceFilePath())) {
            profile.setTradeLicenceFilePath(org.getTradeLicenceFilePath());
        }
        if (profile.getTradeLicenceExpiry() == null && org.getTradeLicenceExpiry() != null) {
            profile.setTradeLicenceExpiry(org.getTradeLicenceExpiry());
        }
        if (!StringUtils.hasText(profile.getLegalCompanyName()) && StringUtils.hasText(org.getLegalCompanyName())) {
            profile.setLegalCompanyName(org.getLegalCompanyName());
        }
    }

    private ScCompanyProfile wrapProfile(ScOrganization org, ScTenantMembership membership) {
        ScCompanyProfile profile = new ScCompanyProfile();
        profile.setUuid(org.getUuid());
        profile.setOrganizationUuid(org.getUuid());
        profile.setLegalCompanyName(org.getLegalCompanyName());
        profile.setTradeLicenceNumber(org.getTradeLicenceNumber());
        profile.setTradeLicenceFilePath(org.getTradeLicenceFilePath());
        profile.setTradeLicenceExpiry(org.getTradeLicenceExpiry());
        profile.setStatus(membership.getStatus());
        return profile;
    }

    private List<String> readList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList());
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid trade list");
        }
    }

    private static ScCompanyStatus parseStatus(String raw) {
        try {
            return ScCompanyStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + raw);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }

    private AuthPrincipal requireRoles(Set<Role> allowed) {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        if (principal.getRoles().contains(Role.CLIENT) || principal.getRoles().contains(Role.SUBCONTRACTOR)) {
            if (principal.getRoles().size() == 1) {
                throw new ForbiddenException("Staff access required");
            }
        }
        for (Role role : allowed) {
            if (principal.getRoles().contains(role)) {
                return principal;
            }
        }
        throw new ForbiddenException("QS or Project Manager access required");
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static boolean isDemoInviteEmail(String email) {
        return email != null && email.toLowerCase().endsWith(DEMO_EMAIL_DOMAIN);
    }
}
