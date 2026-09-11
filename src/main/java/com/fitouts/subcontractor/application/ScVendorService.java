package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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
import com.fitouts.account.application.AccountService;
import com.fitouts.account.application.ClientAccountConversionResult;
import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScInviteVendorRequest;
import com.fitouts.subcontractor.api.ScPrequalificationReviewRequest;
import com.fitouts.subcontractor.api.ScVendorSummaryResponse;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScCompanyStatus;
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

    /** Demo / local invites use the same password as V15 seeded users. */
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
    private final ScRegistrationInviteRepository inviteRepository;
    private final AccountService accountService;
    private final ClientPortalInviteService portalInviteService;
    private final ScOrganizationResolver organizationResolver;
    private final ScEligibilityService eligibilityService;
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
    public ScVendorSummaryResponse getVendor(UUID organizationUuid) {
        requireRoles(PREQUAL_ROLES);
        UUID tenantId = requireCompany();
        ScTenantMembership membership = membershipRepository
                .findByOrganizationUuidAndCompanyId(organizationUuid, tenantId)
                .orElseThrow(() -> new NotFoundException("Subcontractor vendor not found"));
        ScOrganization org = organizationRepository.findById(organizationUuid)
                .orElseThrow(() -> new NotFoundException("Subcontractor organization not found"));
        return toSummary(org, membership);
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
        if (request.getApprovedTrades() != null) {
            membership.setApprovedTrades(writeJson(request.getApprovedTrades()));
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
                .orElseGet(() -> wrapProfile(org, membership));
        String compliance = eligibilityService.computeComplianceStatus(profile).name();
        return ScVendorSummaryResponse.builder()
                .organizationUuid(org.getUuid())
                .legalCompanyName(org.getLegalCompanyName())
                .primaryContactEmail(org.getPrimaryContactEmail())
                .status(membership.getStatus().name())
                .complianceStatus(compliance)
                .performanceScore(org.getPerformanceScore())
                .tradeCategories(readList(org.getTradeCategories()))
                .approvedTrades(readList(membership.getApprovedTrades()))
                .maxPackageValue(membership.getMaxPackageValue())
                .reviewedAt(membership.getReviewedAt() != null ? membership.getReviewedAt().toString() : null)
                .prequalificationNotes(membership.getPrequalificationNotes())
                .build();
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
