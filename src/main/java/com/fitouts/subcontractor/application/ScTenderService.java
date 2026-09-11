package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
import com.fitouts.subcontractor.api.ScAddBiddersRequest;
import com.fitouts.subcontractor.api.ScAwardPackResponse;
import com.fitouts.subcontractor.api.ScAwardRequest;
import com.fitouts.subcontractor.api.ScBidderResponse;
import com.fitouts.subcontractor.api.ScClarificationAnswerRequest;
import com.fitouts.subcontractor.api.ScClarificationRequest;
import com.fitouts.subcontractor.api.ScClarificationResponse;
import com.fitouts.subcontractor.api.ScComparisonBidderRow;
import com.fitouts.subcontractor.api.ScComparisonResponse;
import com.fitouts.subcontractor.api.ScIssueRfqRequest;
import com.fitouts.subcontractor.api.ScQuoteDraftRequest;
import com.fitouts.subcontractor.api.ScQuoteLineRequest;
import com.fitouts.subcontractor.api.ScQuoteLineResponse;
import com.fitouts.subcontractor.api.ScQuoteResponse;
import com.fitouts.subcontractor.api.ScRfqSummaryResponse;
import com.fitouts.subcontractor.api.ScAppointmentEligibilityResponse;
import com.fitouts.subcontractor.domain.ScBidderStatus;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.ScPackageAwardRepository;
import com.fitouts.subcontractor.domain.ScPackageBidder;
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
import com.fitouts.subcontractor.domain.ScTenderStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScTenderService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private final SubcontractorPackageRepository packageRepository;
    private final ScPackageBidderRepository bidderRepository;
    private final ScQuoteRepository quoteRepository;
    private final ScQuoteLineRepository quoteLineRepository;
    private final ScPackageClarificationRepository clarificationRepository;
    private final ScPackageAwardRepository awardRepository;
    private final ScOrganizationRepository organizationRepository;
    private final ScCompanyProfileRepository profileRepository;
    private final ScEligibilityService eligibilityService;
    private final ScPortalAccessService portalAccessService;
    private final ScPortalUserRepository portalUserRepository;
    private final ProjectService projectService;

    @Transactional
    public List<ScBidderResponse> addBidders(Long projectId, UUID packageUuid, ScAddBiddersRequest request) {
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
                continue;
            }
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

        ScQuote quote;
        if (request != null && request.getQuoteUuid() != null) {
            quote = quoteRepository.findById(request.getQuoteUuid())
                    .orElseThrow(() -> new NotFoundException("Quote not found"));
            if (!quote.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
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
            for (ScQuoteLineRequest lineReq : request.getLines()) {
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

    @Transactional
    public ScQuoteResponse submitQuote(UUID packageUuid, UUID quoteUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireTenderingAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = requireIssuedRfq(packageUuid, companyId);
        requireBidder(pkg.getUuid(), portalUser.getOrganizationUuid());
        assertBeforeDeadline(pkg);

        ScQuote quote = quoteRepository.findById(quoteUuid)
                .orElseThrow(() -> new NotFoundException("Quote not found"));
        if (!quote.getPackageUuid().equals(packageUuid)
                || !quote.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("Not your quote");
        }
        if (quote.getStatus() != ScQuoteStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT quotes can be submitted");
        }
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
                .toList();
        List<ScPackageClarification> combined = new ArrayList<>(own);
        combined.addAll(material);
        combined.sort(Comparator.comparing(ScPackageClarification::getCreatedAt));
        return combined.stream().map(this::toClarificationResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScComparisonResponse getComparison(Long projectId, UUID packageUuid) {
        requireStaff();
        SubcontractorPackage pkg = requirePackage(projectId, packageUuid);
        boolean sealed = isSealed(pkg);
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
            // While sealed: show who submitted, hide rates / quote identifiers.
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
                .bidders(rows)
                .build();
    }

    @Transactional
    public ScAwardPackResponse awardPackage(Long projectId, UUID packageUuid, ScAwardRequest request) {
        requireStaff();
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
        }

        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid).orElseGet(ScPackageAward::new);
        award.setPackageUuid(packageUuid);
        award.setOrganizationUuid(request.getOrganizationUuid());
        award.setQuoteUuid(quote != null ? quote.getUuid() : null);
        award.setAwardedValue(awardedValue);
        award.setAwardedAt(OffsetDateTime.now());
        awardRepository.save(award);

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
        if (pkg.getStatus() != SubcontractorPackageStatus.COMPLETE
                && pkg.getStatus() != SubcontractorPackageStatus.IN_PROGRESS) {
            pkg.setStatus(SubcontractorPackageStatus.APPOINTED);
        }
        pkg.setTenderStatus(ScTenderStatus.AWARDED);
        packageRepository.save(pkg);
        return getAwardPack(projectId, packageUuid);
    }

    @Transactional(readOnly = true)
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
                .contractStatus(award.getContractStatus().name())
                .contractFilePath(award.getContractFilePath())
                .adminSignedAt(award.getAdminSignedAt())
                .adminSignerName(award.getAdminSignerName())
                .signedAt(award.getSignedAt())
                .signatureAuditJson(award.getSignatureAuditJson())
                .build();
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
        return ScBidderResponse.builder()
                .uuid(bidder.getUuid())
                .organizationUuid(bidder.getOrganizationUuid())
                .organizationName(org != null ? org.getLegalCompanyName() : null)
                .status(bidder.getStatus().name())
                .invitedAt(bidder.getInvitedAt())
                .viewedAt(bidder.getViewedAt())
                .eligible(eligible)
                .eligibilityJson(bidder.getEligibilityJson())
                .build();
    }

    private ScRfqSummaryResponse toRfqSummary(SubcontractorPackage pkg, ScPackageBidder bidder, boolean sealed) {
        Project project = resolveProject(pkg.getProjectId());
        return ScRfqSummaryResponse.builder()
                .packageUuid(pkg.getUuid())
                .packageName(pkg.getName())
                .projectId(pkg.getProjectId())
                .projectName(project != null ? project.getName() : null)
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
                .build();
    }

    private ScClarificationResponse toClarificationResponse(ScPackageClarification row) {
        return ScClarificationResponse.builder()
                .uuid(row.getUuid())
                .organizationUuid(row.getOrganizationUuid())
                .question(row.getQuestion())
                .answer(row.getAnswer())
                .material(row.isMaterial())
                .issuedToAllAt(row.getIssuedToAllAt())
                .createdAt(row.getCreatedAt())
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
