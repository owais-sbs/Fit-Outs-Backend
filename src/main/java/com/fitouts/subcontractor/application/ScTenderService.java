package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.subcontractor.api.ScAddBiddersRequest;
import com.fitouts.subcontractor.api.ScAwardBoqLineResponse;
import com.fitouts.subcontractor.api.ScAwardPackResponse;
import com.fitouts.subcontractor.api.ScAwardPackSectionStatus;
import com.fitouts.subcontractor.api.ScAwardRequest;
import com.fitouts.subcontractor.api.ScBidderResponse;
import com.fitouts.subcontractor.api.ScBoqLineView;
import com.fitouts.subcontractor.api.ScClarificationAnswerRequest;
import com.fitouts.subcontractor.api.ScClarificationRequest;
import com.fitouts.subcontractor.api.ScClarificationResponse;
import com.fitouts.subcontractor.api.ScComparisonLineCell;
import com.fitouts.subcontractor.api.ScComparisonScopeLine;
import com.fitouts.subcontractor.api.ScComparisonBidderRow;
import com.fitouts.subcontractor.api.ScComparisonResponse;
import com.fitouts.subcontractor.api.ScIssueRfqRequest;
import com.fitouts.subcontractor.api.ScQuoteDraftRequest;
import com.fitouts.subcontractor.api.ScQuoteLineRequest;
import com.fitouts.subcontractor.api.ScQuoteLineResponse;
import com.fitouts.subcontractor.api.ScQuoteResponse;
import com.fitouts.subcontractor.api.ScRfqSummaryResponse;
import com.fitouts.subcontractor.domain.ScAwardBoqLine;
import com.fitouts.subcontractor.domain.ScAwardBoqLineRepository;
import com.fitouts.subcontractor.domain.ScBidderStatus;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScContractStatus;
import com.fitouts.subcontractor.domain.ScFreeIssueMaterial;
import com.fitouts.subcontractor.domain.ScFreeIssueMaterialRepository;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPackageAttendance;
import com.fitouts.subcontractor.domain.ScPackageAttendanceRepository;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.ScPackageAwardRepository;
import com.fitouts.subcontractor.domain.ScPackageBidder;
import com.fitouts.subcontractor.domain.SubcontractorPackageBoqLine;
import com.fitouts.subcontractor.domain.SubcontractorPackageBoqLineRepository;
import com.fitouts.subcontractor.domain.ScPackageBidderRepository;
import com.fitouts.subcontractor.domain.ScPackageClarification;
import com.fitouts.subcontractor.domain.ScPackageClarificationRepository;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScQuote;
import com.fitouts.subcontractor.domain.ScQuoteLine;
import com.fitouts.subcontractor.domain.ScQuoteLineRepository;
import com.fitouts.subcontractor.domain.ScQuoteLineStatus;
import com.fitouts.subcontractor.domain.ScQuoteRepository;
import com.fitouts.subcontractor.domain.ScQuoteStatus;
import com.fitouts.subcontractor.domain.ScTenantMembership;
import com.fitouts.subcontractor.domain.ScTenantMembershipRepository;
import com.fitouts.subcontractor.domain.ScTenderStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;
import com.fitouts.completion.application.CommercialLifecycleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScTenderService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private final SubcontractorPackageRepository packageRepository;
    private final ScPackageBidderRepository bidderRepository;
    private final SubcontractorPackageBoqLineRepository packageBoqLineRepository;
    private final ScQuoteRepository quoteRepository;
    private final ScQuoteLineRepository quoteLineRepository;
    private final ScPackageClarificationRepository clarificationRepository;
    private final ScPackageAwardRepository awardRepository;
    private final ScOrganizationRepository organizationRepository;
    private final ScCompanyProfileRepository profileRepository;
    private final ScTenantMembershipRepository membershipRepository;
    private final ScEligibilityService eligibilityService;
    private final ScPortalAccessService portalAccessService;
    private final ScPortalUserRepository portalUserRepository;
    private final ProjectService projectService;
    private final CommercialLifecycleService commercialLifecycleService;
    private final ObjectMapper objectMapper;
    private final ScAwardBoqLineRepository awardBoqLineRepository;
    private final BoqLineRepository boqLineRepository;
    private final ScFreeIssueMaterialRepository freeIssueMaterialRepository;
    private final ScPackageAttendanceRepository packageAttendanceRepository;
    private final ScContractSignatureService contractSignatureService;
    @Transactional
    public List<ScBidderResponse> addBidders(Long projectId, UUID packageUuid, ScAddBiddersRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        if (request == null || request.getOrganizationUuids() == null || request.getOrganizationUuids().isEmpty()) {
            throw new BadRequestException("organizationUuids is required");
        }
        UUID companyId = requireCompany();
        List<ScBidderResponse> result = new ArrayList<>();
        for (UUID orgUuid : request.getOrganizationUuids()) {
            if (orgUuid == null) {
                continue;
            }
            organizationRepository.findById(orgUuid)
                    .orElseThrow(() -> new NotFoundException("Organization not found: " + orgUuid));
            ScPackageBidder bidder = bidderRepository.findByPackageUuidAndOrganizationUuid(packageUuid, orgUuid)
                    .orElseGet(() -> {
                        ScPackageBidder row = new ScPackageBidder();
                        row.setPackageUuid(packageUuid);
                        row.setOrganizationUuid(orgUuid);
                        row.setCompanyId(companyId);
                        row.setInvitedAt(OffsetDateTime.now());
                        row.setStatus(ScBidderStatus.INVITED);
                        return row;
                    });
            if (bidder.getInvitedAt() == null) {
                bidder.setInvitedAt(OffsetDateTime.now());
            }
            var eligibility = eligibilityService.checkEligibility(orgUuid, projectId, packageUuid);
            if (!eligibility.isEligible()) {
                throw new BadRequestException("Vendor is not eligible: "
                        + eligibility.getBlockers().stream().map(b -> b.getReason()).toList());
            }
            bidder.setEligibilityJson(writeEligibility(eligibility));
            bidder.setStatus(ScBidderStatus.INVITED);
            bidder = bidderRepository.save(bidder);
            result.add(toBidderResponse(bidder, pkg));
        }
        if (pkg.getTenderStatus() == null) {
            pkg.setTenderStatus(ScTenderStatus.DRAFT);
            packageRepository.save(pkg);
        }
        return result;
    }

    @Transactional
    public ScRfqSummaryResponse issueRfq(Long projectId, UUID packageUuid, ScIssueRfqRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        if (request == null || request.getTenderDeadline() == null) {
            throw new BadRequestException("tenderDeadline is required");
        }
        if (request.getTenderDeadline().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("tenderDeadline must be in the future");
        }
        long bidderCount = bidderRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(packageUuid, requireCompany())
                .size();
        if (bidderCount == 0) {
            throw new BadRequestException("Add at least one bidder before issuing RFQ");
        }
        List<String> invalidBidders = new ArrayList<>();
        for (ScPackageBidder bidder : bidderRepository
                .findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(packageUuid, requireCompany())) {
            var eligibility = eligibilityService.checkEligibility(
                    bidder.getOrganizationUuid(), projectId, packageUuid);
            bidder.setEligibilityJson(writeEligibility(eligibility));
            bidderRepository.save(bidder);
            if (!eligibility.isEligible()) {
                invalidBidders.add(bidder.getOrganizationUuid() + ": "
                        + eligibility.getBlockers().stream().map(b -> b.getReason()).toList());
            }
        }
        if (!invalidBidders.isEmpty()) {
            throw new BadRequestException("Vendor no longer eligible: " + invalidBidders);
        }
        pkg.setTenderStatus(ScTenderStatus.ISSUED);
        pkg.setTenderDeadline(request.getTenderDeadline());
        pkg.setTenderIssuedAt(OffsetDateTime.now());
        pkg.setQuoteValidityDays(request.getQuoteValidityDays());
        pkg.setPaymentTerms(trimToNull(request.getPaymentTerms()));
        pkg.setRetentionPct(request.getRetentionPct());
        pkg.setSiteVisitAt(request.getSiteVisitAt());
        pkg.setTenderDescription(trimToNull(request.getTenderDescription()));
        packageRepository.save(pkg);
        return toRfqSummary(pkg, null, true);
    }

    @Transactional(readOnly = true)
    public List<ScBidderResponse> listEligibleBidders(Long projectId, UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        UUID companyId = requireCompany();
        List<ScBidderResponse> result = new ArrayList<>();
        for (ScCompanyProfile profile : profileRepository.findByCompanyIdOrderByLegalCompanyNameAsc(companyId)) {
            if (profile.getOrganizationUuid() == null || profile.getAdminAccountId() == null) {
                continue;
            }
            ScAppointmentEligibilityResponse eligibility = eligibilityService.checkAppointmentEligibility(
                    profile.getAdminAccountId(), pkg);
            if (!eligibility.isEligible()) {
            ScOrganization org = organizationRepository.findById(profile.getOrganizationUuid()).orElse(null);
            ScPackageBidder existing = bidderRepository
                    .findByPackageUuidAndOrganizationUuid(packageUuid, profile.getOrganizationUuid())
                    .orElse(null);
            result.add(ScBidderResponse.builder()
                    .uuid(existing != null ? existing.getUuid() : null)
                    .organizationUuid(profile.getOrganizationUuid())
                    .organizationName(org != null ? org.getLegalCompanyName() : profile.getLegalCompanyName())
                    .status(existing != null ? existing.getStatus().name() : "NOT_INVITED")
                    .invitedAt(existing != null ? existing.getInvitedAt() : null)
                    .viewedAt(existing != null ? existing.getViewedAt() : null)
                    .eligible(true)
                    .eligibilityJson(existing != null ? existing.getEligibilityJson() : null)
                    .build());
        }
        return result;
    }

        // Same source as vendor directory (tenant memberships), not only legacy profiles.
        for (ScTenantMembership membership : membershipRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId)) {
            UUID orgUuid = membership.getOrganizationUuid();
            if (orgUuid == null) {
            ScOrganization org = organizationRepository.findById(orgUuid).orElse(null);
            if (org == null) {
            var eligibility = eligibilityService.checkEligibility(orgUuid, projectId, packageUuid);
                    .findByPackageUuidAndOrganizationUuid(packageUuid, orgUuid)
            String name = org.getLegalCompanyName();
            if (!StringUtils.hasText(name)) {
                ScCompanyProfile profile = profileRepository
                        .findByOrganizationUuidAndCompanyId(orgUuid, companyId)
                        .orElse(null);
                name = profile != null ? profile.getLegalCompanyName() : null;
                    .organizationUuid(orgUuid)
                    .organizationName(StringUtils.hasText(name) ? name : "Subcontractor")
                    .eligible(eligibility.isEligible())
                    .eligibility(eligibility)
        result.sort(Comparator.comparing(
                ScBidderResponse::getOrganizationName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

    private String writeEligibility(Object eligibility) {
        try {
            return objectMapper.writeValueAsString(eligibility);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Could not save eligibility snapshot");

    @Transactional(readOnly = true)
    public List<ScBidderResponse> listBidders(Long projectId, UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        return bidderRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(packageUuid, requireCompany())
                .stream()
                .map(b -> toBidderResponse(b, pkg))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScRfqSummaryResponse> listMyRfqs() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        List<ScPackageBidder> bidders = bidderRepository
                .findByOrganizationUuidAndCompanyIdOrderByInvitedAtDesc(portalUser.getOrganizationUuid(), companyId);
        List<ScRfqSummaryResponse> result = new ArrayList<>();
        for (ScPackageBidder bidder : bidders) {
            SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(bidder.getPackageUuid(), companyId)
                    .orElse(null);
            if (pkg == null || pkg.getTenderStatus() == null
                    || pkg.getTenderStatus() == ScTenderStatus.DRAFT) {
                continue;
            }
            result.add(toRfqSummary(pkg, bidder, isSealed(pkg)));
        }
        return result;
    }

    @Transactional
    public ScRfqSummaryResponse getRfqForBidder(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Package not found"));
        ScPackageBidder bidder = bidderRepository
                .findByPackageUuidAndOrganizationUuid(packageUuid, portalUser.getOrganizationUuid())
                .orElseThrow(() -> new ForbiddenException("Not invited to this RFQ"));
        if (bidder.getViewedAt() == null) {
            bidder.setViewedAt(OffsetDateTime.now());
            if (bidder.getStatus() == ScBidderStatus.INVITED) {
                bidder.setStatus(ScBidderStatus.VIEWED);
            }
            bidderRepository.save(bidder);
        }
        return toRfqSummary(pkg, bidder, isSealed(pkg));
    }

    @Transactional
    public ScQuoteResponse saveQuoteDraft(UUID packageUuid, ScQuoteDraftRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = requireIssuedRfq(packageUuid, companyId);
        requireBidder(pkg.getUuid(), portalUser.getOrganizationUuid());
        assertBeforeDeadline(pkg);
        commercialLifecycleService.assertNotArchived(pkg.getProjectId());

        ScQuote quote;
        if (request != null && request.getQuoteUuid() != null) {
            quote = quoteRepository.findById(request.getQuoteUuid())
                    .orElseThrow(() -> new NotFoundException("Quote not found"));
            if (!quote.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            if (!quote.getPackageUuid().equals(packageUuid)
                    || !quote.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
                throw new ForbiddenException("Not your quote");
            }
            if (quote.getStatus() != ScQuoteStatus.DRAFT) {
                throw new BadRequestException("Only DRAFT quotes can be edited");
            }
        } else {
            List<ScQuote> existing = quoteRepository.findByPackageUuidAndOrganizationUuidOrderByVersionDesc(
                    packageUuid, portalUser.getOrganizationUuid());
            int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
            quote = new ScQuote();
            quote.setPackageUuid(packageUuid);
            quote.setOrganizationUuid(portalUser.getOrganizationUuid());
            quote.setVersion(nextVersion);
            quote.setStatus(ScQuoteStatus.DRAFT);
        }

        if (request != null) {
            quote.setLeadTimeDays(request.getLeadTimeDays());
            quote.setExclusionsText(trimToNull(request.getExclusionsText()));
            quote.setQualificationsText(trimToNull(request.getQualificationsText()));
            quote.setValidityDate(request.getValidityDate());
        }
        quote = quoteRepository.save(quote);

        if (request != null && request.getLines() != null) {
            quoteLineRepository.deleteByQuoteUuid(quote.getUuid());
            BigDecimal total = BigDecimal.ZERO;
            Set<UUID> packageScope = packageScope(pkg);
            for (ScQuoteLineRequest lineReq : request.getLines()) {
                if (lineReq.getBoqLineId() == null || !packageScope.contains(lineReq.getBoqLineId())) {
                    throw new BadRequestException("Quote line is outside the package BOQ scope");
                }
                ScQuoteLine line = new ScQuoteLine();
                line.setQuoteUuid(quote.getUuid());
                line.setBoqLineId(lineReq.getBoqLineId());
                line.setRate(lineReq.getRate());
                line.setQuantity(lineReq.getQuantity());
                line.setLineStatus(parseLineStatus(lineReq.getLineStatus()));
                line.setRemarks(trimToNull(lineReq.getRemarks()));
                if (line.getRate() != null && line.getQuantity() != null) {
                    line.setAmount(line.getRate().multiply(line.getQuantity()).setScale(2, RoundingMode.HALF_UP));
                    if (line.getLineStatus() == ScQuoteLineStatus.QUOTED) {
                        total = total.add(line.getAmount());
                    }
                }
                quoteLineRepository.save(line);
            }
            quote.setTotalValue(total);
            quote = quoteRepository.save(quote);
        }
        return toQuoteResponse(quote, true);
    }

    private Set<UUID> packageScope(SubcontractorPackage pkg) {
        Set<UUID> scope = new java.util.HashSet<>(packageBoqLineRepository
                .findByPackageUuidAndCompanyIdOrderBySortOrderAsc(pkg.getUuid(), pkg.getCompanyId())
                .stream().map(x -> x.getBoqLineId()).toList());
        if (scope.isEmpty() && pkg.getBoqLineId() != null) {
            scope.add(pkg.getBoqLineId());
        }
        return scope;
    }

    @Transactional
    public ScQuoteResponse submitQuote(UUID packageUuid, UUID quoteUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = requireIssuedRfq(packageUuid, companyId);
        requireBidder(pkg.getUuid(), portalUser.getOrganizationUuid());
        assertBeforeDeadline(pkg);
        commercialLifecycleService.assertNotArchived(pkg.getProjectId());

        ScQuote quote = quoteRepository.findById(quoteUuid)
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        if (!quote.getPackageUuid().equals(packageUuid)
                || !quote.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("Not your quote");
        }
        if (quote.getStatus() != ScQuoteStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT quotes can be submitted");
        }
        quoteRepository.findByPackageUuidAndOrganizationUuidOrderByVersionDesc(
                packageUuid, portalUser.getOrganizationUuid()).stream()
                .filter(q -> !q.getUuid().equals(quote.getUuid())
                        && q.getStatus() == ScQuoteStatus.SUBMITTED)
                .forEach(q -> {
                    q.setStatus(ScQuoteStatus.SUPERSEDED);
                    quoteRepository.save(q);
                });
        quote.setStatus(ScQuoteStatus.SUBMITTED);
        quote.setSubmittedAt(OffsetDateTime.now());
        quoteRepository.save(quote);

        ScPackageBidder bidder = requireBidder(packageUuid, portalUser.getOrganizationUuid());
        bidder.setStatus(ScBidderStatus.SUBMITTED);
        bidderRepository.save(bidder);
        return toQuoteResponse(quote, true);
    }

    @Transactional(readOnly = true)
    public List<ScQuoteResponse> getMyBids() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return quoteRepository.findByOrganizationUuidOrderByUpdatedAtDesc(portalUser.getOrganizationUuid())
                .stream()
                .map(q -> {
                    SubcontractorPackage pkg = packageRepository.findById(q.getPackageUuid()).orElse(null);
                    boolean showRates = pkg == null || !isSealed(pkg)
                            || q.getOrganizationUuid().equals(portalUser.getOrganizationUuid());
                    return toQuoteResponse(q, showRates);
        UUID companyId = requireCompany();
                    SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(q.getPackageUuid(), companyId)
                            .orElseGet(() -> packageRepository.findById(q.getPackageUuid()).orElse(null));
                    ScPackageBidder bidder = bidderRepository
                            .findByPackageUuidAndOrganizationUuid(q.getPackageUuid(), portalUser.getOrganizationUuid())
                            .orElse(null);
                    return toQuoteResponse(q, showRates, pkg, bidder);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScQuoteResponse> getMyBidsForPackage(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        requireBidder(packageUuid, portalUser.getOrganizationUuid());
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        return quoteRepository.findByPackageUuidAndOrganizationUuidOrderByVersionDesc(
                        packageUuid, portalUser.getOrganizationUuid())
                .stream()
                .map(q -> toQuoteResponse(q, true))
                .toList();
    }

    @Transactional
    public ScClarificationResponse addClarification(UUID packageUuid, ScClarificationRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new BadRequestException("question is required");
        }
        requireBidder(packageUuid, portalUser.getOrganizationUuid());
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        commercialLifecycleService.assertNotArchived(pkg.getProjectId());
        ScPackageClarification row = new ScPackageClarification();
        row.setPackageUuid(packageUuid);
        row.setOrganizationUuid(portalUser.getOrganizationUuid());
        row.setQuestion(request.getQuestion().trim());
        row.setAskedByAccountId(principal.getAccountId());
        return toClarificationResponse(clarificationRepository.save(row));
    }

    @Transactional
    public ScClarificationResponse answerClarification(
            Long projectId, UUID packageUuid, UUID clarificationUuid, ScClarificationAnswerRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
        AuthPrincipal principal = requireStaff();
        requirePackage(projectId, packageUuid);
        if (request == null || !StringUtils.hasText(request.getAnswer())) {
            throw new BadRequestException("answer is required");
        }
        ScPackageClarification row = clarificationRepository.findById(clarificationUuid)
                .orElseThrow(() -> new NotFoundException("Clarification not found"));
        if (!row.getPackageUuid().equals(packageUuid)) {
            throw new BadRequestException("Clarification does not belong to this package");
        }
        row.setAnswer(request.getAnswer().trim());
        row.setMaterial(request.isMaterial());
        row.setAnsweredByAccountId(principal.getAccountId());
        if (request.isMaterial()) {
            row.setIssuedToAllAt(OffsetDateTime.now());
        }
        return toClarificationResponse(clarificationRepository.save(row));
    }

    @Transactional(readOnly = true)
    public List<ScClarificationResponse> listClarifications(Long projectId, UUID packageUuid) {
        requireStaff();
        requirePackage(projectId, packageUuid);
        return clarificationRepository.findByPackageUuidOrderByCreatedAtAsc(packageUuid)
                .stream()
                .map(this::toClarificationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScClarificationResponse> listMyClarifications(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        requireBidder(packageUuid, portalUser.getOrganizationUuid());
        List<ScPackageClarification> own = clarificationRepository
                .findByPackageUuidAndOrganizationUuidOrderByCreatedAtAsc(packageUuid, portalUser.getOrganizationUuid());
        List<ScPackageClarification> material = clarificationRepository.findByPackageUuidOrderByCreatedAtAsc(packageUuid)
                .stream()
                .filter(c -> c.isMaterial() && c.getIssuedToAllAt() != null)
                .filter(c -> !Objects.equals(c.getOrganizationUuid(), portalUser.getOrganizationUuid()))
        return clarificationsVisibleToBidder(packageUuid, portalUser.getOrganizationUuid()).stream()
                .map(this::toClarificationResponse)
                .toList();
    }

    /**
     * All clarification threads across RFQs this subcontractor is invited to (own Q&amp;A + material addenda).
     */
    @Transactional(readOnly = true)
    public List<ScClarificationResponse> listAllMyClarifications() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        UUID orgUuid = portalUser.getOrganizationUuid();

        List<ScClarificationResponse> out = new ArrayList<>();
        for (ScPackageBidder bidder : bidderRepository
                .findByOrganizationUuidAndCompanyIdOrderByInvitedAtDesc(orgUuid, companyId)) {
            SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(bidder.getPackageUuid(), companyId)
                    .orElse(null);
            if (pkg == null || pkg.getTenderStatus() == null
                    || pkg.getTenderStatus() == ScTenderStatus.DRAFT) {
                continue;
            }
            Project project = resolveProject(pkg.getProjectId());
            for (ScPackageClarification row : clarificationsVisibleToBidder(pkg.getUuid(), orgUuid)) {
                out.add(toClarificationResponse(row, pkg, project));
        }
        out.sort(Comparator.comparing(
                ScClarificationResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;

    private List<ScPackageClarification> clarificationsVisibleToBidder(UUID packageUuid, UUID organizationUuid) {
                .findByPackageUuidAndOrganizationUuidOrderByCreatedAtAsc(packageUuid, organizationUuid);
                .filter(c -> !Objects.equals(c.getOrganizationUuid(), organizationUuid))
                .toList();
        List<ScPackageClarification> combined = new ArrayList<>(own);
        combined.addAll(material);
        combined.sort(Comparator.comparing(ScPackageClarification::getCreatedAt));
        return combined.stream().map(this::toClarificationResponse).toList();
        return combined;
    }

    @Transactional(readOnly = true)
    public ScComparisonResponse getComparison(Long projectId, UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        boolean sealed = isSealed(pkg);
        boolean deadlinePassed = pkg.getTenderDeadline() != null
                && !OffsetDateTime.now().isBefore(pkg.getTenderDeadline());
        boolean showRates = !sealed;

        List<ScComparisonScopeLine> scopeLines = buildComparisonScope(pkg);

        List<ScComparisonBidderRow> rows = new ArrayList<>();
        for (ScPackageBidder bidder : bidderRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(
                packageUuid, requireCompany())) {
            ScOrganization org = organizationRepository.findById(bidder.getOrganizationUuid()).orElse(null);
            ScQuote submitted = quoteRepository
                    .findByPackageUuidAndOrganizationUuidOrderByVersionDesc(packageUuid, bidder.getOrganizationUuid())
                    .stream()
                    .filter(q -> q.getStatus() == ScQuoteStatus.SUBMITTED)
                    .findFirst()
                    .orElse(null);

            List<ScQuoteLine> quoteLines = submitted == null
                    ? List.of()
                    : quoteLineRepository.findByQuoteUuidOrderByBoqLineIdAsc(submitted.getUuid());
            Map<UUID, ScQuoteLine> byBoq = new HashMap<>();
            for (ScQuoteLine ql : quoteLines) {
                if (ql.getBoqLineId() != null) {
                    byBoq.put(ql.getBoqLineId(), ql);
                }
            }

            List<ScComparisonLineCell> cells = new ArrayList<>();
            List<String> gaps = new ArrayList<>();
            BigDecimal prelim = BigDecimal.ZERO;
            BigDecimal provisional = BigDecimal.ZERO;
            int quoted = 0;
            int excluded = 0;
            int alternative = 0;

            for (ScComparisonScopeLine scope : scopeLines) {
                ScQuoteLine ql = byBoq.get(scope.getBoqLineId());
                String status = ql == null ? "MISSING" : (ql.getLineStatus() != null ? ql.getLineStatus().name() : "QUOTED");
                BigDecimal amount = showRates && ql != null ? ql.getAmount() : null;
                BigDecimal rate = showRates && ql != null ? ql.getRate() : null;
                if ("EXCLUDED".equals(status) || "MISSING".equals(status)) {
                    excluded++;
                    gaps.add(scope.getDescription() != null ? scope.getDescription() : String.valueOf(scope.getBoqLineId()));
                } else if ("ALTERNATIVE".equals(status)) {
                    alternative++;
                } else if ("QUOTED".equals(status) || "CLARIFICATION".equals(status)) {
                    quoted++;
                }
                if (showRates && amount != null) {
                    if (scope.isPreliminaries()) {
                        prelim = prelim.add(amount);
                    }
                    if (scope.isProvisionalSum()) {
                        provisional = provisional.add(amount);
                    }
                }
                cells.add(ScComparisonLineCell.builder()
                        .boqLineId(scope.getBoqLineId())
                        .description(scope.getDescription())
                        .sectionCode(scope.getSectionCode())
                        .quantity(scope.getQuantity())
                        .unit(scope.getUnit())
                        .rate(rate)
                        .amount(amount)
                        .lineStatus(status)
                        .remarks(showRates && ql != null ? ql.getRemarks() : null)
                        .cheapest(false)
                        .build());
            }

            rows.add(ScComparisonBidderRow.builder()
                    .organizationUuid(bidder.getOrganizationUuid())
                    .organizationName(org != null ? org.getLegalCompanyName() : null)
                    .bidderStatus(bidder.getStatus().name())
                    .quoteUuid(sealed || submitted == null ? null : submitted.getUuid())
                    .totalValue(sealed || submitted == null ? null : submitted.getTotalValue())
                    .leadTimeDays(sealed || submitted == null ? null : submitted.getLeadTimeDays())
                    .build());
        }
        return ScComparisonResponse.builder()
                .packageUuid(packageUuid)
                .deadlinePassed(!sealed)
                    .quoteUuid(showRates && submitted != null ? submitted.getUuid() : null)
                    .totalValue(showRates && submitted != null ? submitted.getTotalValue() : null)
                    .leadTimeDays(submitted != null ? submitted.getLeadTimeDays() : null)
                    .exclusionsText(showRates && submitted != null ? submitted.getExclusionsText() : null)
                    .qualificationsText(showRates && submitted != null ? submitted.getQualificationsText() : null)
                    .paymentTerms(pkg.getPaymentTerms())
                    .retentionPct(pkg.getRetentionPct())
                    .preliminariesValue(showRates ? prelim : null)
                    .provisionalSumsValue(showRates ? provisional : null)
                    .quotedLineCount(quoted)
                    .excludedLineCount(excluded)
                    .alternativeLineCount(alternative)
                    .scopeGaps(showRates ? gaps : List.of())
                    .lines(cells)
                    .cheapestTotal(false)

        if (showRates) {
            markCheapestTotals(rows);
            markCheapestLines(rows, scopeLines);

                .packageName(pkg.getName())
                .deadlinePassed(deadlinePassed)
                .sealed(sealed)
                .paymentTerms(pkg.getPaymentTerms())
                .retentionPct(pkg.getRetentionPct())
                .scopeLines(scopeLines)
                .bidders(rows)
                .build();
    }

    private List<ScComparisonScopeLine> buildComparisonScope(SubcontractorPackage pkg) {
        List<UUID> scopeIds = packageBoqLineRepository
                .findByPackageUuidAndCompanyIdOrderBySortOrderAsc(pkg.getUuid(), pkg.getCompanyId())
                .stream()
                .map(SubcontractorPackageBoqLine::getBoqLineId)
                .toList();
        if (scopeIds.isEmpty() && pkg.getBoqLineId() != null) {
            scopeIds = List.of(pkg.getBoqLineId());
        }
        List<ScComparisonScopeLine> out = new ArrayList<>();
        for (UUID id : scopeIds) {
            BoqLine line = boqLineRepository.findById(id).orElse(null);
            if (line == null) continue;
            String desc = line.getDescription() != null ? line.getDescription() : "";
            String section = StringUtils.hasText(line.getCategoryCode())
                    ? line.getCategoryCode().trim()
                    : (StringUtils.hasText(line.getCategoryName()) ? line.getCategoryName().trim() : "GENERAL");
            String hay = (section + " " + desc).toLowerCase(Locale.ROOT);
            out.add(ScComparisonScopeLine.builder()
                    .boqLineId(line.getId())
                    .description(line.getDescription())
                    .sectionCode(section)
                    .quantity(line.getQuantity())
                    .unit(line.getUnit())
                    .preliminaries(hay.contains("prelim"))
                    .provisionalSum(hay.contains("provisional") || hay.matches(".*\\bps\\b.*") || hay.contains("prime cost"))
                    .build());
        }
        return out;
    }

    private static void markCheapestTotals(List<ScComparisonBidderRow> rows) {
        BigDecimal min = null;
        for (ScComparisonBidderRow row : rows) {
            if (row.getTotalValue() == null) continue;
            if (min == null || row.getTotalValue().compareTo(min) < 0) {
                min = row.getTotalValue();
            }
        }
        if (min == null) return;
        for (ScComparisonBidderRow row : rows) {
            row.setCheapestTotal(row.getTotalValue() != null && row.getTotalValue().compareTo(min) == 0);
        }
    }

    private static void markCheapestLines(List<ScComparisonBidderRow> rows, List<ScComparisonScopeLine> scopeLines) {
        for (ScComparisonScopeLine scope : scopeLines) {
            BigDecimal min = null;
            for (ScComparisonBidderRow row : rows) {
                ScComparisonLineCell cell = findCell(row, scope.getBoqLineId());
                if (cell == null || cell.getAmount() == null) continue;
                if (!"QUOTED".equals(cell.getLineStatus()) && !"CLARIFICATION".equals(cell.getLineStatus())
                        && !"ALTERNATIVE".equals(cell.getLineStatus())) {
                    continue;
                }
                if (min == null || cell.getAmount().compareTo(min) < 0) {
                    min = cell.getAmount();
                }
            }
            if (min == null) continue;
            for (ScComparisonBidderRow row : rows) {
                ScComparisonLineCell cell = findCell(row, scope.getBoqLineId());
                if (cell != null && cell.getAmount() != null && cell.getAmount().compareTo(min) == 0) {
                    cell.setCheapest(true);
                }
            }
        }
    }

    private static ScComparisonLineCell findCell(ScComparisonBidderRow row, UUID boqLineId) {
        if (row.getLines() == null) return null;
        for (ScComparisonLineCell cell : row.getLines()) {
            if (Objects.equals(cell.getBoqLineId(), boqLineId)) return cell;
        }
        return null;
    }

    @Transactional
    public ScAwardPackResponse awardPackage(Long projectId, UUID packageUuid, ScAwardRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
        AuthPrincipal staff = requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        if (isSealed(pkg)) {
            throw new BadRequestException("Cannot award before tender deadline");
        }
        if (request == null || request.getOrganizationUuid() == null) {
            throw new BadRequestException("organizationUuid is required");
        }
        ScPackageBidder bidder = requireBidder(packageUuid, request.getOrganizationUuid());
        ScQuote quote = null;
        if (request.getQuoteUuid() != null) {
            quote = quoteRepository.findById(request.getQuoteUuid())
                    .orElseThrow(() -> new NotFoundException("Quote not found"));
        } else {
            quote = quoteRepository
                    .findByPackageUuidAndOrganizationUuidOrderByVersionDesc(packageUuid, request.getOrganizationUuid())
                    .stream()
                    .filter(q -> q.getStatus() == ScQuoteStatus.SUBMITTED)
                    .findFirst()
                    .orElse(null);
        }
        BigDecimal awardedValue = request.getAwardedValue();
        if (awardedValue == null && quote != null) {
            awardedValue = quote.getTotalValue();
        ScQuote quote = request.getQuoteUuid() == null ? null : quoteRepository.findById(request.getQuoteUuid())
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        if (quote == null
                || !packageUuid.equals(quote.getPackageUuid())
                || !request.getOrganizationUuid().equals(quote.getOrganizationUuid())
                || quote.getStatus() != ScQuoteStatus.SUBMITTED
                || !quoteRepository.findByPackageUuidAndOrganizationUuidOrderByVersionDesc(
                        packageUuid, request.getOrganizationUuid()).stream()
                        .filter(q -> q.getStatus() == ScQuoteStatus.SUBMITTED)
                        .findFirst().map(q -> q.getUuid().equals(quote.getUuid())).orElse(false)) {
            throw new BadRequestException("Award requires the bidder's current submitted quote");
        var eligibility = eligibilityService.checkEligibility(
                request.getOrganizationUuid(), projectId, packageUuid);
        if (!eligibility.isEligible()) {
            throw new BadRequestException("Award blocked by eligibility: "
                    + eligibility.getBlockers().stream().map(b -> b.getReason()).toList());
        if (awardedValue == null) {
        } else if (quote.getTotalValue() != null
                && awardedValue.compareTo(quote.getTotalValue()) != 0
                && !StringUtils.hasText(request.getAwardValueReason())) {
            throw new BadRequestException("Award value difference requires a reason");
        }

        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid).orElseGet(ScPackageAward::new);
        award.setPackageUuid(packageUuid);
        award.setOrganizationUuid(request.getOrganizationUuid());
        award.setQuoteUuid(quote != null ? quote.getUuid() : null);
        award.setAwardedValue(awardedValue);
        award.setAwardValueReason(trimToNull(request.getAwardValueReason()));
        award.setAwardedByAccountId(staff.getAccountId());
        award.setAwardedAt(OffsetDateTime.now());
        award = awardRepository.save(award);
        freezeAwardedBoqSnapshot(award, quote);

        bidder.setStatus(ScBidderStatus.AWARDED);
        bidderRepository.save(bidder);
        bidderRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(packageUuid, requireCompany())
                .stream()
                .filter(b -> !b.getOrganizationUuid().equals(request.getOrganizationUuid()))
                .filter(b -> b.getStatus() != ScBidderStatus.REGRET)
                .forEach(b -> {
                    b.setStatus(ScBidderStatus.REGRET);
                    b.setRegretSentAt(OffsetDateTime.now());
                    bidderRepository.save(b);
                });

        // Appoint winning org onto the package so it appears in SC "My packages" / Projects.
        Long adminAccountId = resolveOrgAdminAccountId(request.getOrganizationUuid(), requireCompany());
        if (adminAccountId == null) {
            throw new BadRequestException(
                    "Winning organization has no SC Admin account — cannot appoint package");
        }
        ScOrganization winningOrg = organizationRepository.findById(request.getOrganizationUuid()).orElse(null);
        pkg.setAppointedAccountId(adminAccountId);
        pkg.setAppointedCompanyName(winningOrg != null ? winningOrg.getLegalCompanyName() : null);
        if (pkg.getOriginalAwardValue() == null) {
            pkg.setOriginalAwardValue(awardedValue);
        }
        if (pkg.getStatus() != SubcontractorPackageStatus.COMPLETE
                && pkg.getStatus() != SubcontractorPackageStatus.IN_PROGRESS) {
            pkg.setStatus(SubcontractorPackageStatus.APPOINTED);
        }
        pkg.setTenderStatus(ScTenderStatus.AWARDED);
        packageRepository.save(pkg);
        return getAwardPack(projectId, packageUuid);
    }

    @Transactional(readOnly = true)
    public List<ScAwardPackResponse> listMyAwardPacks() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireActivePortalUser(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        List<ScAwardPackResponse> result = new ArrayList<>();
        for (ScPackageAward award : awardRepository.findByOrganizationUuid(portalUser.getOrganizationUuid())) {
            SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(award.getPackageUuid(), companyId)
                    .orElse(null);
            if (pkg == null) {
                continue;
            }
            result.add(buildAwardPack(pkg, award));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ScAwardPackResponse getMyAwardPack(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireActivePortalUser(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid)
                .orElseThrow(() -> new NotFoundException("No award recorded for this package"));
        if (!award.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("Not your award pack");
        }
        return buildAwardPack(pkg, award);
    }

    @Transactional
    public ScAwardPackResponse getAwardPack(Long projectId, UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid)
                .orElseThrow(() -> new NotFoundException("No award recorded for this package"));
        ScOrganization org = organizationRepository.findById(award.getOrganizationUuid()).orElse(null);
        return ScAwardPackResponse.builder()
                .uuid(award.getUuid())
                .packageUuid(packageUuid)
                .packageName(pkg.getName())
                .organizationUuid(award.getOrganizationUuid())
                .organizationName(org != null ? org.getLegalCompanyName() : null)
                .quoteUuid(award.getQuoteUuid())
                .awardedValue(award.getAwardedValue())
                .awardedAt(award.getAwardedAt())
        contractSignatureService.refreshStoredContractPdf(pkg, award, org);
        return buildAwardPack(pkg, award);
    }

    private void freezeAwardedBoqSnapshot(ScPackageAward award, ScQuote quote) {
        awardBoqLineRepository.deleteByAwardUuid(award.getUuid());
        if (quote == null) {
            return;
        }
        List<ScQuoteLine> lines = quoteLineRepository.findByQuoteUuidOrderByBoqLineIdAsc(quote.getUuid());
        int order = 0;
        for (ScQuoteLine line : lines) {
            ScAwardBoqLine snap = new ScAwardBoqLine();
            snap.setAwardUuid(award.getUuid());
            snap.setPackageUuid(award.getPackageUuid());
            snap.setCompanyId(requireCompany());
            snap.setQuoteUuid(quote.getUuid());
            snap.setQuoteLineUuid(line.getUuid());
            snap.setBoqLineId(line.getBoqLineId());
            snap.setQuantity(line.getQuantity());
            snap.setRate(line.getRate());
            snap.setAmount(line.getAmount());
            snap.setLineStatus(line.getLineStatus() != null ? line.getLineStatus().name() : "QUOTED");
            snap.setRemarks(line.getRemarks());
            snap.setSortOrder(order++);
            if (line.getBoqLineId() != null) {
                boqLineRepository.findById(line.getBoqLineId()).ifPresent(boq -> {
                    snap.setSectionCode(boq.getCategoryCode());
                    snap.setDescription(boq.getDescription());
                    snap.setUnit(boq.getUnit());
                });
            }
            awardBoqLineRepository.save(snap);

    private ScAwardPackResponse buildAwardPack(SubcontractorPackage pkg, ScPackageAward award) {
        ScOrganization org = organizationRepository.findById(award.getOrganizationUuid()).orElse(null);
        Project project = resolveProject(pkg.getProjectId());
        ScQuote quote = award.getQuoteUuid() == null ? null
                : quoteRepository.findById(award.getQuoteUuid()).orElse(null);
        List<ScAwardBoqLineResponse> awardedLines = awardBoqLineRepository
                .findByAwardUuidOrderBySortOrderAsc(award.getUuid())
                .stream()
                .map(line -> ScAwardBoqLineResponse.builder()
                        .uuid(line.getUuid())
                        .boqLineId(line.getBoqLineId())
                        .sectionCode(line.getSectionCode())
                        .description(line.getDescription())
                        .unit(line.getUnit())
                        .quantity(line.getQuantity())
                        .rate(line.getRate())
                        .amount(line.getAmount())
                        .lineStatus(line.getLineStatus())
                        .remarks(line.getRemarks())
                        .build())
                .toList();
        // Fallback for legacy awards before snapshot existed: surface live quote lines once.
        if (awardedLines.isEmpty() && quote != null) {
            awardedLines = quoteLineRepository.findByQuoteUuidOrderByBoqLineIdAsc(quote.getUuid()).stream()
                    .map(line -> {
                        BoqLine boq = line.getBoqLineId() == null ? null
                                : boqLineRepository.findById(line.getBoqLineId()).orElse(null);
                        return ScAwardBoqLineResponse.builder()
                                .uuid(line.getUuid())
                                .boqLineId(line.getBoqLineId())
                                .sectionCode(boq != null ? boq.getCategoryCode() : null)
                                .description(boq != null ? boq.getDescription() : null)
                                .unit(boq != null ? boq.getUnit() : null)
                                .quantity(line.getQuantity())
                                .rate(line.getRate())
                                .amount(line.getAmount())
                                .lineStatus(line.getLineStatus() != null ? line.getLineStatus().name() : null)
                                .remarks(line.getRemarks())
                                .build();
                    })
                    .toList();
        List<ScFreeIssueMaterial> freeIssue = freeIssueMaterialRepository
                .findByPackageUuidAndCompanyIdOrderBySortOrderAsc(pkg.getUuid(), pkg.getCompanyId());
        List<ScPackageAttendance> attendance = packageAttendanceRepository
                .packageUuid(pkg.getUuid())
                .projectId(pkg.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .projectLocation(project != null ? project.getLocation() : null)
                .organizationName(org != null ? org.getLegalCompanyName() : pkg.getAppointedCompanyName())
                .quoteVersion(quote != null ? quote.getVersion() : null)
                .quotedValue(quote != null ? quote.getTotalValue() : null)
                .originalAwardValue(pkg.getOriginalAwardValue() != null
                        ? pkg.getOriginalAwardValue() : award.getAwardedValue())
                .awardValueReason(award.getAwardValueReason())
                .packageStatus(pkg.getStatus() != null ? pkg.getStatus().name() : null)
                .tenderStatus(pkg.getTenderStatus() != null ? pkg.getTenderStatus().name() : null)
                .contractStatus(award.getContractStatus().name())
                .contractFilePath(award.getContractFilePath())
                .adminSignedAt(award.getAdminSignedAt())
                .adminSignerName(award.getAdminSignerName())
                .adminSignerTitle(award.getAdminSignerTitle())
                .signedAt(award.getSignedAt())
                .subcontractorSignerName(award.getSubcontractorSignerName())
                .subcontractorSignerTitle(award.getSubcontractorSignerTitle())
                .signatureAuditJson(award.getSignatureAuditJson())
                .tradePackageCode(pkg.getTradePackageCode())
                .tradePackageName(pkg.getTradePackageName())
                .paymentTerms(pkg.getPaymentTerms())
                .retentionPct(pkg.getRetentionPct())
                .ldTerms(pkg.getLdTerms())
                .plannedStart(pkg.getPlannedStart())
                .plannedFinish(pkg.getPlannedFinish())
                .activityCodes(pkg.getActivityCodes())
                .tenderDescription(pkg.getTenderDescription())
                .exclusionsText(quote != null ? quote.getExclusionsText() : null)
                .qualificationsText(quote != null ? quote.getQualificationsText() : null)
                .specialistLicenceRequired(pkg.getSpecialistLicenceRequired())
                .awardedBoqLines(awardedLines)
                .freeIssueMaterials(freeIssue)
                .attendanceMatrix(attendance)
                .sections(awardPackSections(pkg, award, quote, awardedLines, freeIssue, attendance, org))
                .closeoutGaps(closeoutGaps(pkg, award))
                .build();
    }

    private List<ScAwardPackSectionStatus> awardPackSections(
            SubcontractorPackage pkg,
            ScPackageAward award,
            ScQuote quote,
            List<ScAwardBoqLineResponse> lines,
            List<ScFreeIssueMaterial> freeIssue,
            List<ScPackageAttendance> attendance,
            ScOrganization org) {
        List<ScAwardPackSectionStatus> sections = new ArrayList<>();
        boolean contractReady = StringUtils.hasText(award.getContractFilePath())
                || (award.getContractStatus() != null
                        && award.getContractStatus() != ScContractStatus.WAITING_FOR_ADMIN_SIGNATURE);

        String awardLetterValue = contractReady
                ? "JCT subcontract agreement PDF — " + (award.getContractStatus() != null
                        ? award.getContractStatus().name().replace('_', ' ')
                        : "available")
                : "Pending Main Contractor digital signature (JCT cover-letter format)";
        sections.add(section("AWARD_LETTER", "Award letter / LOI",
                contractReady ? "CONNECTED" : "PARTIAL",
                awardLetterValue,
                "Official JCT-style subcontract agreement generated on signature"));

        int lineCount = lines != null ? lines.size() : 0;
        BigDecimal boqTotal = BigDecimal.ZERO;
        if (lines != null) {
            for (ScAwardBoqLineResponse line : lines) {
                if (line.getAmount() != null) {
                    boqTotal = boqTotal.add(line.getAmount());
                }
            }
        }
        sections.add(section("AGREED_BOQ", "Agreed priced BOQ",
                lineCount > 0 ? "CONNECTED" : "PARTIAL",
                lineCount > 0
                        ? lineCount + " priced line(s) · AED " + money(boqTotal)
                        + (award.getAwardedValue() != null ? " · Award AED " + money(award.getAwardedValue()) : "")
                        : "No frozen award BOQ lines yet",
                lineCount > 0 ? "Frozen award snapshot from winning quote" : "Awaiting award snapshot"));

        String inclusions = firstNonBlank(pkg.getTenderDescription(),
                tradeLabel(pkg),
                "Trade package scope as tendered");
        sections.add(section("INCLUSIONS", "Scope inclusions",
                StringUtils.hasText(pkg.getTenderDescription()) || StringUtils.hasText(pkg.getTradePackageName())
                        ? "CONNECTED" : "PARTIAL",
                clip(inclusions, 220),
                "From package tender description / trade package"));

        String exclusions = quote != null ? quote.getExclusionsText() : null;
        sections.add(section("EXCLUSIONS", "Scope exclusions",
                StringUtils.hasText(exclusions) ? "CONNECTED" : "PARTIAL",
                StringUtils.hasText(exclusions) ? clip(exclusions, 220) : "No exclusions recorded on awarded quote",
                "From awarded quote exclusions"));

        sections.add(section("DRAWINGS", "Current drawing revision", "PARTIAL",
                "Refer to package Drawings tab for current revision set",
                "Award-pack drawing lock uses live package drawings"));

        sections.add(section("SPECIFICATION", "Specification / material schedule", "PARTIAL",
                "Per agreed BOQ descriptions and project specifications",
                "Material schedule follows awarded BOQ line specs"));

        String programme = programmeSummary(pkg);
        sections.add(section("PROGRAMME", "Programme extract",
                pkg.getPlannedStart() != null || pkg.getPlannedFinish() != null
                        || StringUtils.hasText(pkg.getActivityCodes()) ? "CONNECTED" : "PARTIAL",
                programme,
                "Package planned dates / activity codes"));

        sections.add(section("MILESTONES", "Key milestones / access dates",
                pkg.getPlannedStart() != null || pkg.getPlannedFinish() != null ? "CONNECTED" : "PARTIAL",
                programme,
                "Start / finish from package programme"));

        sections.add(section("PAYMENT_TERMS", "Payment terms",
                StringUtils.hasText(pkg.getPaymentTerms()) ? "CONNECTED" : "PARTIAL",
                StringUtils.hasText(pkg.getPaymentTerms())
                        ? clip(pkg.getPaymentTerms(), 220)
                        : "JCT standard payment terms apply (see agreement T&Cs)",
                "Package payment terms"));

        sections.add(section("RETENTION", "Retention / DLP",
                pkg.getRetentionPct() != null ? "CONNECTED" : "PARTIAL",
                pkg.getRetentionPct() != null
                        ? pkg.getRetentionPct().stripTrailingZeros().toPlainString()
                        + "% retention · DLP 12 months (JCT standard)"
                        : "DLP 12 months (JCT standard) · retention % not set on package",
                "Retention % from award package"));

        sections.add(section("LD_TERMS", "LD / delay recovery",
                StringUtils.hasText(pkg.getLdTerms()) ? "CONNECTED" : "PARTIAL",
                StringUtils.hasText(pkg.getLdTerms())
                        ? clip(pkg.getLdTerms(), 220)
                        : "Per JCT delay / non-payment recovery clauses in agreement T&Cs",
                "Package LD terms"));

        sections.add(section("INSURANCE", "Insurance requirements", "CONNECTED",
                "Subcontractor must maintain insurance per prequalification / eligibility compliance",
                "Enforced via SC compliance dossier"));

        sections.add(section("COMMUNITY_RULES", "Community / site rules", "CONNECTED",
                "Community registration and site access rules enforced at eligibility",
                "Checked before mobilisation"));

        String licence = pkg.getSpecialistLicenceRequired();
        sections.add(section("PERMITS", "Required permits",
                StringUtils.hasText(licence) ? "CONNECTED" : "PARTIAL",
                StringUtils.hasText(licence)
                        ? "Specialist licence required: " + clip(licence, 180)
                        : "Standard site permits / NOC as applicable to the works",
                "Package specialist licence requirement"));

        sections.add(section("HSE", "HSE / induction / PTW", "PARTIAL",
                "Worker induction tracked on package roster; site HSE rules apply",
                "Induction / PTW via mobilisation roster"));

        int freeCount = freeIssue != null ? freeIssue.size() : 0;
        String freeValue = freeCount == 0
                ? "No free-issue items listed"
                : freeCount + " item(s): " + freeIssue.stream()
                        .limit(3)
                        .map(m -> m.getItemDescription() != null ? m.getItemDescription() : "item")
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("");
        sections.add(section("FREE_ISSUE", "Free-issue materials",
                freeCount > 0 ? "CONNECTED" : "PARTIAL",
                clip(freeValue, 220),
                "Package free-issue schedule"));

        int attCount = attendance != null ? attendance.size() : 0;
        String attValue = attCount == 0
                ? "No attendance responsibilities listed"
                : attCount + " responsibility row(s): " + attendance.stream()
                        .limit(3)
                        .map(a -> (a.getResponsibilityType() != null ? a.getResponsibilityType() : "duty")
                                + (a.getResponsibleParty() != null ? " → " + a.getResponsibleParty().name() : ""))
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("");
        sections.add(section("ATTENDANCE", "Attendance matrix",
                attCount > 0 ? "CONNECTED" : "PARTIAL",
                clip(attValue, 220),
                "Package attendance matrix"));

        sections.add(section("VARIATION_PROCEDURE", "Variation procedure", "CONNECTED",
                "Variations require written instruction; work outside BOQ treated as additional works (JCT T&Cs)",
                "JCT variation / additional works clause"));

        sections.add(section("CLAIM_PROCEDURE", "Claim / measurement procedure", "CONNECTED",
                "Progress claims measured against awarded BOQ; final payment on actual site measurement (JCT)",
                "Claim / measure / certify pipeline"));

        sections.add(section("BACK_CHARGE_POLICY", "Back-charge policy", "CONNECTED",
                "Back-charges recoverable for incomplete / defective works or third-party rectification",
                "Package back-charge records + JCT compensation clauses"));

        sections.add(section("QUALITY_ITP", "Quality / ITP / hold points", "CONNECTED",
                "Inspection requests and hold points linked to this package",
                "Quality / inspection module"));

        sections.add(section("SUBMITTALS", "Submittal / samples / shop drawings", "CONNECTED",
                "Shop drawings and material samples via package submittal workflow",
                "Submittal module"));

        sections.add(section("WARRANTY_OM", "Warranty / O&M", "CONNECTED",
                "Defect Liability Period: 12 months from client move-in (JCT standard)",
                "JCT DLP / warranty clause"));

        String contactValue = (org != null && StringUtils.hasText(org.getLegalCompanyName())
                ? org.getLegalCompanyName()
                : "Appointed subcontractor")
                + (award.getAdminSignerName() != null ? " · Admin signatory: " + award.getAdminSignerName() : "")
                + (award.getSubcontractorSignerName() != null
                        ? " · SC signatory: " + award.getSubcontractorSignerName() : "");
        sections.add(section("CONTACTS", "Contacts / escalation / signatures",
                award.getContractStatus() != null ? "CONNECTED" : "PARTIAL",
                clip(contactValue, 220),
                "Contract signature parties"));
        return sections;
    }

    private static String programmeSummary(SubcontractorPackage pkg) {
        StringBuilder sb = new StringBuilder();
        if (pkg.getPlannedStart() != null) {
            sb.append("Start ").append(pkg.getPlannedStart());
        }
        if (pkg.getPlannedFinish() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Finish ").append(pkg.getPlannedFinish());
        }
        if (StringUtils.hasText(pkg.getActivityCodes())) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Activities: ").append(pkg.getActivityCodes().trim());
        }
        if (sb.length() == 0) {
            return "Programme dates not set on package — refer to master schedule";
        }
        return sb.toString();
    }

    private static String tradeLabel(SubcontractorPackage pkg) {
        if (StringUtils.hasText(pkg.getTradePackageName()) && StringUtils.hasText(pkg.getTradePackageCode())) {
            return pkg.getTradePackageCode() + " — " + pkg.getTradePackageName();
        }
        if (StringUtils.hasText(pkg.getTradePackageName())) return pkg.getTradePackageName();
        if (StringUtils.hasText(pkg.getTradePackageCode())) return pkg.getTradePackageCode();
        return null;
    }

    private static String money(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String clip(String text, int max) {
        if (!StringUtils.hasText(text)) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }

    private static ScAwardPackSectionStatus section(
            String code, String label, String status, String value, String note) {
        return ScAwardPackSectionStatus.builder()
                .code(code)
                .label(label)
                .status(status)
                .value(value)
                .note(note)
                .build();
    }

    private List<String> closeoutGaps(SubcontractorPackage pkg, ScPackageAward award) {
        List<String> gaps = new ArrayList<>();
        if (award.getSignedAt() == null) {
            gaps.add("Contract not fully signed");
        }
        if (pkg.getStatus() == SubcontractorPackageStatus.OPEN) {
            gaps.add("Package not appointed / mobilised");
        }
        return gaps;
    }

    private ScBidderResponse toBidderResponse(ScPackageBidder bidder, SubcontractorPackage pkg) {
        ScOrganization org = organizationRepository.findById(bidder.getOrganizationUuid()).orElse(null);
        ScCompanyProfile profile = profileRepository
                .findByOrganizationUuidAndCompanyId(bidder.getOrganizationUuid(), bidder.getCompanyId())
                .orElse(null);
        boolean eligible = false;
        if (profile != null && profile.getAdminAccountId() != null) {
            eligible = eligibilityService.checkAppointmentEligibility(profile.getAdminAccountId(), pkg).isEligible();
        }
        var eligibility = profile == null ? null : eligibilityService.checkEligibility(
                bidder.getOrganizationUuid(), pkg.getProjectId(), pkg.getUuid());
        return ScBidderResponse.builder()
                .uuid(bidder.getUuid())
                .organizationUuid(bidder.getOrganizationUuid())
                .organizationName(org != null ? org.getLegalCompanyName() : null)
                .status(bidder.getStatus().name())
                .invitedAt(bidder.getInvitedAt())
                .viewedAt(bidder.getViewedAt())
                .eligible(eligible)
                .eligibilityJson(bidder.getEligibilityJson())
                .eligible(eligibility != null && eligibility.isEligible())
                .eligibility(eligibility)
                .build();
    }

    private ScRfqSummaryResponse toRfqSummary(SubcontractorPackage pkg, ScPackageBidder bidder, boolean sealed) {
        Project project = resolveProject(pkg.getProjectId());
        boolean deadlinePassed = pkg.getTenderDeadline() != null
                && !OffsetDateTime.now().isBefore(pkg.getTenderDeadline());
        return ScRfqSummaryResponse.builder()
                .packageUuid(pkg.getUuid())
                .packageName(pkg.getName())
                .projectId(pkg.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .tradePackageCode(pkg.getTradePackageCode())
                .tradePackageName(pkg.getTradePackageName())
                .tenderStatus(pkg.getTenderStatus() != null ? pkg.getTenderStatus().name() : null)
                .tenderDeadline(pkg.getTenderDeadline())
                .tenderIssuedAt(pkg.getTenderIssuedAt())
                .quoteValidityDays(pkg.getQuoteValidityDays())
                .paymentTerms(pkg.getPaymentTerms())
                .retentionPct(pkg.getRetentionPct())
                .siteVisitAt(pkg.getSiteVisitAt())
                .tenderDescription(pkg.getTenderDescription())
                .bidderStatus(bidder != null ? bidder.getStatus().name() : null)
                .sealed(sealed)
                .deadlinePassed(!sealed)
                .build();
    }

    private ScQuoteResponse toQuoteResponse(ScQuote quote, boolean showRates) {
                .deadlinePassed(deadlinePassed)
                .regretMessage(regretMessageFor(bidder, pkg, project))
                .regretSentAt(bidder != null ? bidder.getRegretSentAt() : null)
                .boqLines(rfqBoqLinesWithoutRates(pkg))

    private static String regretMessageFor(ScPackageBidder bidder, SubcontractorPackage pkg, Project project) {
        if (bidder == null || bidder.getStatus() != ScBidderStatus.REGRET) {
            return null;
        }
        String packageLabel = pkg != null && StringUtils.hasText(pkg.getName()) ? pkg.getName() : "this package";
        String projectLabel = project != null && StringUtils.hasText(project.getName())
                ? project.getName()
                : (pkg != null && pkg.getProjectId() != null ? "Project #" + pkg.getProjectId() : "the project");
        return "Thank you for your tender on " + packageLabel + " (" + projectLabel
                + "). We regret to inform you that your bid was not successful on this occasion. "
                + "We appreciate your participation and welcome future tenders.";

    /** Bidder-facing BOQ scope — never expose internal estimate rates. */
    private List<ScBoqLineView> rfqBoqLinesWithoutRates(SubcontractorPackage pkg) {
        List<ScBoqLineView> lines = new ArrayList<>();
        List<UUID> scopeIds = new ArrayList<>(packageBoqLineRepository
                .findByPackageUuidAndCompanyIdOrderBySortOrderAsc(pkg.getUuid(), pkg.getCompanyId())
                .stream().map(x -> x.getBoqLineId()).toList());
        if (scopeIds.isEmpty() && pkg.getBoqLineId() != null) {
            scopeIds.add(pkg.getBoqLineId());
        for (UUID boqLineId : scopeIds) {
            BoqLine line = boqLineRepository.findById(boqLineId).orElse(null);
            if (line == null) {
                continue;
            }
            String section = StringUtils.hasText(line.getCategoryCode())
                    ? line.getCategoryCode().trim()
                    : (StringUtils.hasText(line.getCategoryName()) ? line.getCategoryName().trim() : "GENERAL");
            lines.add(ScBoqLineView.builder()
                    .boqLineId(line.getId())
                    .packageUuid(pkg.getUuid())
                    .projectId(pkg.getProjectId())
                    .packageName(pkg.getName())
                    .sectionCode(section)
                    .description(line.getDescription())
                    .unit(line.getUnit())
                    .roomLabel(line.getRoomLabel())
                    .floorLabel(line.getFloorLabel())
                    .plannedQty(line.getQuantity())
                    .rate(null)
                    .amount(null)
                    .build());
        return lines;

        return toQuoteResponse(quote, showRates, null, null);

    private ScQuoteResponse toQuoteResponse(
            ScQuote quote, boolean showRates, SubcontractorPackage pkg, ScPackageBidder bidder) {
        List<ScQuoteLineResponse> lines = quoteLineRepository.findByQuoteUuidOrderByBoqLineIdAsc(quote.getUuid())
                .stream()
                .map(line -> ScQuoteLineResponse.builder()
                        .uuid(line.getUuid())
                        .boqLineId(line.getBoqLineId())
                        .rate(showRates ? line.getRate() : null)
                        .quantity(line.getQuantity())
                        .amount(showRates ? line.getAmount() : null)
                        .lineStatus(line.getLineStatus().name())
                        .remarks(line.getRemarks())
                        .build())
                .toList();
        Project project = pkg != null ? resolveProject(pkg.getProjectId()) : null;
        return ScQuoteResponse.builder()
                .uuid(quote.getUuid())
                .packageUuid(quote.getPackageUuid())
                .organizationUuid(quote.getOrganizationUuid())
                .version(quote.getVersion())
                .status(quote.getStatus().name())
                .totalValue(showRates ? quote.getTotalValue() : null)
                .leadTimeDays(quote.getLeadTimeDays())
                .exclusionsText(quote.getExclusionsText())
                .qualificationsText(quote.getQualificationsText())
                .validityDate(quote.getValidityDate())
                .submittedAt(quote.getSubmittedAt())
                .lines(lines)
                .ratesVisible(showRates)
                .bidderStatus(bidder != null ? bidder.getStatus().name() : null)
                .regretMessage(regretMessageFor(bidder, pkg, project))
                .regretSentAt(bidder != null ? bidder.getRegretSentAt() : null)
                .packageName(pkg != null ? pkg.getName() : null)
                .projectName(project != null ? project.getName() : null)
                .projectId(pkg != null ? pkg.getProjectId() : null)
                .build();
    }

    private ScClarificationResponse toClarificationResponse(ScPackageClarification row) {
        return toClarificationResponse(row, null, null);
    }

    private ScClarificationResponse toClarificationResponse(
            ScPackageClarification row, SubcontractorPackage pkg, Project project) {
        return ScClarificationResponse.builder()
                .uuid(row.getUuid())
                .organizationUuid(row.getOrganizationUuid())
                .question(row.getQuestion())
                .answer(row.getAnswer())
                .material(row.isMaterial())
                .issuedToAllAt(row.getIssuedToAllAt())
                .createdAt(row.getCreatedAt())
                .packageUuid(pkg != null ? pkg.getUuid() : row.getPackageUuid())
                .packageName(pkg != null ? pkg.getName() : null)
                .projectId(project != null ? project.getId() : (pkg != null ? pkg.getProjectId() : null))
                .projectName(project != null ? project.getName() : null)
                .build();
    }

    private Long resolveOrgAdminAccountId(UUID organizationUuid, UUID companyId) {
        ScCompanyProfile profile = profileRepository
                .findByOrganizationUuidAndCompanyId(organizationUuid, companyId)
                .orElse(null);
        if (profile != null && profile.getAdminAccountId() != null) {
            return profile.getAdminAccountId();
        }
        return portalUserRepository.findByOrganizationUuidOrderByCreatedAtAsc(organizationUuid).stream()
                .filter(u -> u.getPortalRole() == ScPortalRole.SC_ADMIN)
                .map(ScPortalUser::getAccountId)
                .findFirst()
                .orElseGet(() -> portalUserRepository.findByOrganizationUuidOrderByCreatedAtAsc(organizationUuid)
                        .stream()
                        .map(ScPortalUser::getAccountId)
                        .findFirst()
                        .orElse(null));
    }

    private boolean isSealed(SubcontractorPackage pkg) {
        return pkg.getTenderDeadline() != null && OffsetDateTime.now().isBefore(pkg.getTenderDeadline());
    }

    private void assertBeforeDeadline(SubcontractorPackage pkg) {
        if (!isSealed(pkg) && pkg.getTenderDeadline() != null) {
            throw new BadRequestException("Tender deadline has passed");
        }
        if (pkg.getTenderStatus() != ScTenderStatus.ISSUED) {
            throw new BadRequestException("RFQ is not open for quoting");
        }
    }

    private SubcontractorPackage requireIssuedRfq(UUID packageUuid, UUID companyId) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Package not found"));
        if (pkg.getTenderStatus() != ScTenderStatus.ISSUED) {
            throw new BadRequestException("RFQ is not issued");
        }
        return pkg;
    }

    private ScPackageBidder requireBidder(UUID packageUuid, UUID organizationUuid) {
        return bidderRepository.findByPackageUuidAndOrganizationUuid(packageUuid, organizationUuid)
                .orElseThrow(() -> new ForbiddenException("Not invited to this RFQ"));
    }

    private SubcontractorPackage requirePackage(Long projectId, UUID packageUuid) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        if (!pkg.getProjectId().equals(projectId)) {
            throw new BadRequestException("Package does not belong to this project");
        }
        return pkg;
    }

    private Project resolveProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        try {
            Project project = projectService.getById(projectId);
            UUID companyId = CompanyContext.get();
            if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
                return null;
            }
            return project;
        } catch (Exception e) {
            return null;
        }
    }

    private static ScQuoteLineStatus parseLineStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ScQuoteLineStatus.QUOTED;
        }
        try {
            return ScQuoteLineStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid line status: " + raw);
        }
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Staff access required");
        }
        for (Role role : STAFF_ROLES) {
            if (principal.getRoles().contains(role)) {
                return principal;
            }
        }
        throw new ForbiddenException("QS or Project Manager access required");
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
