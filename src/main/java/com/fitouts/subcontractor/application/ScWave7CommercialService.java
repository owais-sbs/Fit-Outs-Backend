package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
import com.fitouts.subcontractor.api.ScBackChargeRequest;
import com.fitouts.subcontractor.api.ScBackChargeResponse;
import com.fitouts.subcontractor.api.ScCertifyClaimRequest;
import com.fitouts.subcontractor.api.ScClaimStatusTrackerResponse;
import com.fitouts.subcontractor.api.ScMeasureClaimRequest;
import com.fitouts.subcontractor.api.ScPaymentCertificateResponse;
import com.fitouts.subcontractor.api.ScRetentionLedgerResponse;
import com.fitouts.subcontractor.api.ScScorecardResponse;
import com.fitouts.subcontractor.domain.ScBackCharge;
import com.fitouts.subcontractor.domain.ScBackChargeRepository;
import com.fitouts.subcontractor.domain.ScBackChargeStatus;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScOrganization;
import com.fitouts.subcontractor.domain.ScOrganizationRepository;
import com.fitouts.subcontractor.domain.ScPaymentCertificate;
import com.fitouts.subcontractor.domain.ScPaymentCertificateRepository;
import com.fitouts.subcontractor.domain.ScPaymentCertificateStatus;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScRetentionLedger;
import com.fitouts.subcontractor.domain.ScRetentionLedgerRepository;
import com.fitouts.subcontractor.domain.ScRetentionStatus;
import com.fitouts.subcontractor.domain.ScSiteReportRepository;
import com.fitouts.subcontractor.domain.ScSubcontractorScore;
import com.fitouts.subcontractor.domain.ScSubcontractorScoreRepository;
import com.fitouts.subcontractor.domain.ScSubmittalRepository;
import com.fitouts.subcontractor.domain.ScSubmittalStatus;
import com.fitouts.subcontractor.domain.SubcontractorClaim;
import com.fitouts.subcontractor.domain.SubcontractorClaimRepository;
import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScWave7CommercialService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private final SubcontractorClaimRepository claimRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ScPaymentCertificateRepository certificateRepository;
    private final ScRetentionLedgerRepository retentionLedgerRepository;
    private final ScBackChargeRepository backChargeRepository;
    private final ScSubcontractorScoreRepository scoreRepository;
    private final ScSubmittalRepository submittalRepository;
    private final ScSiteReportRepository siteReportRepository;
    private final ScOrganizationRepository organizationRepository;
    private final ScCompanyProfileRepository profileRepository;
    private final ScPortalAccessService portalAccessService;
    private final ProjectService projectService;

    @Transactional
    public SubcontractorClaim measureClaim(Long projectId, UUID claimUuid, ScMeasureClaimRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        SubcontractorClaim claim = requireClaim(claimUuid, projectId);
        if (claim.getStatus() != SubcontractorClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be measured");
        }
        if (request == null) {
            throw new BadRequestException("Measurement values are required");
        }
        claim.setMeasuredQty(request.getMeasuredQty() != null ? request.getMeasuredQty() : claim.getClaimedQty());
        claim.setMeasuredValue(request.getMeasuredValue());
        claim.setMeasuredBy(principal.getAccountId());
        claim.setMeasuredAt(OffsetDateTime.now());
        claim.setStatus(SubcontractorClaimStatus.MEASURED);
        return claimRepository.save(claim);
    }

    @Transactional
    public ScPaymentCertificateResponse certifyClaim(Long projectId, UUID claimUuid, ScCertifyClaimRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        SubcontractorClaim claim = requireClaim(claimUuid, projectId);
        if (claim.getStatus() != SubcontractorClaimStatus.MEASURED) {
            throw new BadRequestException("Only MEASURED claims can be certified");
        }
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(claim.getPackageUuid(), claim.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Package not found"));

        BigDecimal certifiedValue = request != null && request.getCertifiedValue() != null
                ? request.getCertifiedValue()
                : claim.getMeasuredValue();
        if (certifiedValue == null) {
            throw new BadRequestException("certifiedValue is required");
        }

        UUID orgUuid = resolveOrganizationForPackage(pkg);
        BigDecimal retentionPct = pkg.getRetentionPct() != null ? pkg.getRetentionPct() : BigDecimal.ZERO;
        BigDecimal retentionHeld = certifiedValue.multiply(retentionPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        List<ScBackChargeStatus> applicableStatuses = List.of(
                ScBackChargeStatus.OPEN, ScBackChargeStatus.ACKNOWLEDGED);
        BigDecimal backCharges = BigDecimal.ZERO;
        if (orgUuid != null) {
            backCharges = backChargeRepository
                    .findByPackageUuidAndOrganizationUuidAndStatusIn(
                            pkg.getUuid(), orgUuid, applicableStatuses)
                    .stream()
                    .map(ScBackCharge::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal netPayable = certifiedValue.subtract(retentionHeld).subtract(backCharges).max(BigDecimal.ZERO);

        ScPaymentCertificate cert = new ScPaymentCertificate();
        cert.setClaimUuid(claim.getUuid());
        cert.setPackageUuid(pkg.getUuid());
        cert.setProjectId(claim.getProjectId());
        cert.setCompanyId(claim.getCompanyId());
        cert.setOrganizationUuid(orgUuid);
        cert.setCertifiedValue(certifiedValue);
        cert.setRetentionHeld(retentionHeld);
        cert.setBackChargesApplied(backCharges);
        cert.setNetPayable(netPayable);
        cert.setStatus(ScPaymentCertificateStatus.ISSUED);
        if (request != null) {
            cert.setAccountingRef(trimToNull(request.getAccountingRef()));
        }
        final ScPaymentCertificate savedCert = certificateRepository.save(cert);
        final UUID savedCertUuid = savedCert.getUuid();

        if (orgUuid != null && retentionHeld.compareTo(BigDecimal.ZERO) > 0) {
            ScRetentionLedger ledger = new ScRetentionLedger();
            ledger.setPackageUuid(pkg.getUuid());
            ledger.setOrganizationUuid(orgUuid);
            ledger.setCompanyId(claim.getCompanyId());
            ledger.setProjectId(claim.getProjectId());
            ledger.setAmountHeld(retentionHeld);
            ledger.setStatus(ScRetentionStatus.HELD);
            ledger.setCertificateUuid(savedCertUuid);
            retentionLedgerRepository.save(ledger);
        }

        if (orgUuid != null) {
            backChargeRepository
                    .findByPackageUuidAndOrganizationUuidAndStatusIn(pkg.getUuid(), orgUuid, applicableStatuses)
                    .forEach(charge -> {
                        charge.setStatus(ScBackChargeStatus.APPLIED);
                        charge.setAppliedToCertificateUuid(savedCertUuid);
                        backChargeRepository.save(charge);
                    });
        }

        claim.setCertifiedValue(certifiedValue);
        claim.setCertificateUuid(savedCertUuid);
        claim.setStatus(SubcontractorClaimStatus.CERTIFIED);
        claim.setDecidedBy(principal.getAccountId());
        claim.setDecidedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        return toCertificateResponse(savedCert);
    }

    @Transactional
    public SubcontractorClaim markClaimPaid(Long projectId, UUID claimUuid, String accountingRef) {
        requireStaff();
        requireProject(projectId);
        SubcontractorClaim claim = requireClaim(claimUuid, projectId);
        if (claim.getStatus() != SubcontractorClaimStatus.CERTIFIED) {
            throw new BadRequestException("Only CERTIFIED claims can be marked paid");
        }
        ScPaymentCertificate cert = certificateRepository.findByClaimUuid(claim.getUuid())
                .orElseThrow(() -> new NotFoundException("Payment certificate not found"));
        cert.setStatus(ScPaymentCertificateStatus.PAID);
        cert.setPaidDate(LocalDate.now());
        if (StringUtils.hasText(accountingRef)) {
            cert.setAccountingRef(accountingRef.trim());
        }
        certificateRepository.save(cert);
        claim.setStatus(SubcontractorClaimStatus.PAID);
        return claimRepository.save(claim);
    }

    @Transactional(readOnly = true)
    public List<ScPaymentCertificateResponse> listCertificatesForProject(Long projectId) {
        requireStaff();
        requireProject(projectId);
        return certificateRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, requireCompany())
                .stream()
                .map(this::toCertificateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScPaymentCertificateResponse> listMyCertificates() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return certificateRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
                        portalUser.getOrganizationUuid(), requireCompany())
                .stream()
                .map(this::toCertificateResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScRetentionLedgerResponse> listRetentionLedger(Long projectId, UUID packageUuid) {
        requireStaff();
        requireProject(projectId);
        UUID companyId = requireCompany();
        List<ScRetentionLedger> rows = packageUuid != null
                ? retentionLedgerRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(packageUuid, companyId)
                : retentionLedgerRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId);
        return rows.stream().map(this::toRetentionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ScRetentionLedgerResponse> listMyRetentionLedger() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return retentionLedgerRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
                        portalUser.getOrganizationUuid(), requireCompany())
                .stream()
                .map(this::toRetentionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScBackChargeResponse> listBackChargesForProject(Long projectId, UUID packageUuid) {
        requireStaff();
        requireProject(projectId);
        UUID companyId = requireCompany();
        List<ScBackCharge> rows = packageUuid != null
                ? backChargeRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(packageUuid, companyId)
                : backChargeRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId);
        return rows.stream().map(this::toBackChargeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ScBackChargeResponse> listMyBackCharges() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return backChargeRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
                        portalUser.getOrganizationUuid(), requireCompany())
                .stream()
                .map(this::toBackChargeResponse)
                .toList();
    }

    @Transactional
    public ScBackChargeResponse createBackCharge(Long projectId, ScBackChargeRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        if (request == null || request.getPackageUuid() == null || request.getOrganizationUuid() == null
                || request.getAmount() == null) {
            throw new BadRequestException("packageUuid, organizationUuid, and amount are required");
        }
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(request.getPackageUuid(), requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        ScBackCharge charge = new ScBackCharge();
        charge.setPackageUuid(request.getPackageUuid());
        charge.setProjectId(projectId);
        charge.setCompanyId(requireCompany());
        charge.setOrganizationUuid(request.getOrganizationUuid());
        charge.setChargeType(trimToNull(request.getChargeType()));
        charge.setDescription(trimToNull(request.getDescription()));
        charge.setAmount(request.getAmount());
        charge.setEvidencePaths(trimToNull(request.getEvidencePaths()));
        charge.setRaisedBy(principal.getAccountId());
        charge.setStatus(ScBackChargeStatus.OPEN);
        return toBackChargeResponse(backChargeRepository.save(charge));
    }

    @Transactional
    public ScBackChargeResponse acknowledgeBackCharge(UUID backChargeUuid, boolean dispute) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        ScBackCharge charge = backChargeRepository.findByUuidAndCompanyId(backChargeUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Back charge not found"));
        if (!charge.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            throw new ForbiddenException("Not your back charge");
        }
        if (charge.getStatus() != ScBackChargeStatus.OPEN) {
            throw new BadRequestException("Back charge cannot be acknowledged in its current status");
        }
        charge.setAcknowledgedAt(OffsetDateTime.now());
        if (dispute) {
            charge.setDisputed(true);
            charge.setStatus(ScBackChargeStatus.DISPUTED);
        } else {
            charge.setStatus(ScBackChargeStatus.ACKNOWLEDGED);
        }
        return toBackChargeResponse(backChargeRepository.save(charge));
    }

    @Transactional
    public ScScorecardResponse computeAndSaveScorecard(UUID organizationUuid, UUID packageUuid) {
        requireStaff();
        UUID companyId = requireCompany();
        organizationRepository.findById(organizationUuid)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        long approvedSubmittals = submittalRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(organizationUuid, companyId)
                .stream()
                .filter(s -> packageUuid == null || packageUuid.equals(s.getPackageUuid()))
                .filter(s -> s.getStatus() == ScSubmittalStatus.APPROVED)
                .count();
        long totalSubmittals = submittalRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(organizationUuid, companyId)
                .stream()
                .filter(s -> packageUuid == null || packageUuid.equals(s.getPackageUuid()))
                .count();
        long disputedCharges = backChargeRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(organizationUuid, companyId)
                .stream()
                .filter(c -> packageUuid == null || packageUuid.equals(c.getPackageUuid()))
                .filter(ScBackCharge::isDisputed)
                .count();
        long siteReports = siteReportRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .filter(r -> packageUuid == null || packageUuid.equals(r.getPackageUuid()))
                .count();

        BigDecimal quality = scoreFromRatio(approvedSubmittals, totalSubmittals, 80);
        BigDecimal programme = BigDecimal.valueOf(75);
        BigDecimal safety = BigDecimal.valueOf(Math.max(50, 90 - siteReports));
        BigDecimal commercial = BigDecimal.valueOf(Math.max(40, 100 - disputedCharges * 10L));
        BigDecimal responsiveness = BigDecimal.valueOf(70);
        BigDecimal total = quality.add(programme).add(safety).add(commercial).add(responsiveness)
                .divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);

        ScSubcontractorScore score = new ScSubcontractorScore();
        score.setOrganizationUuid(organizationUuid);
        score.setPackageUuid(packageUuid);
        score.setCompanyId(companyId);
        score.setQualityScore(quality);
        score.setProgrammeScore(programme);
        score.setSafetyScore(safety);
        score.setCommercialScore(commercial);
        score.setResponsivenessScore(responsiveness);
        score.setTotalScore(total);
        score.setComputedAt(OffsetDateTime.now());
        score = scoreRepository.save(score);

        organizationRepository.findById(organizationUuid).ifPresent(org -> {
            org.setPerformanceScore(total);
            organizationRepository.save(org);
        });

        return toScorecardResponse(score);
    }

    @Transactional(readOnly = true)
    public List<ScScorecardResponse> listMyScorecards() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return scoreRepository
                .findByOrganizationUuidAndCompanyIdOrderByComputedAtDesc(
                        portalUser.getOrganizationUuid(), requireCompany())
                .stream()
                .map(this::toScorecardResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScScorecardResponse getLatestScorecard(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        Optional<ScSubcontractorScore> score = packageUuid == null
                ? scoreRepository.findFirstByOrganizationUuidAndPackageUuidIsNullOrderByComputedAtDesc(
                        portalUser.getOrganizationUuid())
                : scoreRepository.findFirstByOrganizationUuidAndPackageUuidOrderByComputedAtDesc(
                        portalUser.getOrganizationUuid(), packageUuid);
        return score.map(this::toScorecardResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ScClaimStatusTrackerResponse> getClaimStatusTracker() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireCommercialAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();
        List<SubcontractorPackage> packages = resolvePackagesForOrganization(portalUser.getOrganizationUuid(), companyId);
        return packages.stream()
                .flatMap(pkg -> claimRepository
                        .findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(pkg.getUuid(), companyId)
                        .stream()
                        .map(claim -> ScClaimStatusTrackerResponse.builder()
                                .claimUuid(claim.getUuid())
                                .packageUuid(pkg.getUuid())
                                .packageName(pkg.getName())
                                .projectId(claim.getProjectId())
                                .status(claim.getStatus().name())
                                .claimedQty(claim.getClaimedQty())
                                .measuredQty(claim.getMeasuredQty())
                                .measuredValue(claim.getMeasuredValue())
                                .certifiedValue(claim.getCertifiedValue())
                                .certificateUuid(claim.getCertificateUuid())
                                .submittedAt(claim.getSubmittedAt())
                                .measuredAt(claim.getMeasuredAt())
                                .build()))
                .toList();
    }

    private List<SubcontractorPackage> resolvePackagesForOrganization(UUID organizationUuid, UUID companyId) {
        java.util.Set<Long> accountIds = profileRepository.findByCompanyIdOrderByLegalCompanyNameAsc(companyId)
                .stream()
                .filter(p -> organizationUuid.equals(p.getOrganizationUuid()))
                .map(com.fitouts.subcontractor.domain.ScCompanyProfile::getAdminAccountId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return packageRepository.findByCompanyIdAndAppointedAccountIdInOrderByCreatedAtDesc(companyId, accountIds);
    }

    private BigDecimal scoreFromRatio(long numerator, long denominator, int baseline) {
        if (denominator <= 0) {
            return BigDecimal.valueOf(baseline);
        }
        double ratio = (double) numerator / denominator;
        return BigDecimal.valueOf(Math.min(100, baseline + ratio * 20)).setScale(2, RoundingMode.HALF_UP);
    }

    private UUID resolveOrganizationForPackage(SubcontractorPackage pkg) {
        if (pkg.getAppointedAccountId() == null) {
            return null;
        }
        return profileRepository.findByAdminAccountIdAndCompanyId(pkg.getAppointedAccountId(), pkg.getCompanyId())
                .map(p -> p.getOrganizationUuid())
                .orElse(null);
    }

    private ScPaymentCertificateResponse toCertificateResponse(ScPaymentCertificate cert) {
        return ScPaymentCertificateResponse.builder()
                .uuid(cert.getUuid())
                .claimUuid(cert.getClaimUuid())
                .packageUuid(cert.getPackageUuid())
                .projectId(cert.getProjectId())
                .organizationUuid(cert.getOrganizationUuid())
                .certifiedValue(cert.getCertifiedValue())
                .retentionHeld(cert.getRetentionHeld())
                .backChargesApplied(cert.getBackChargesApplied())
                .netPayable(cert.getNetPayable())
                .status(cert.getStatus().name())
                .paidDate(cert.getPaidDate())
                .accountingRef(cert.getAccountingRef())
                .createdAt(cert.getCreatedAt())
                .build();
    }

    private ScRetentionLedgerResponse toRetentionResponse(ScRetentionLedger row) {
        return ScRetentionLedgerResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .organizationUuid(row.getOrganizationUuid())
                .projectId(row.getProjectId())
                .amountHeld(row.getAmountHeld())
                .releaseDate(row.getReleaseDate())
                .defectLiabilityEnd(row.getDefectLiabilityEnd())
                .status(row.getStatus().name())
                .certificateUuid(row.getCertificateUuid())
                .notes(row.getNotes())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private ScBackChargeResponse toBackChargeResponse(ScBackCharge row) {
        return ScBackChargeResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .projectId(row.getProjectId())
                .organizationUuid(row.getOrganizationUuid())
                .chargeType(row.getChargeType())
                .description(row.getDescription())
                .amount(row.getAmount())
                .evidencePaths(row.getEvidencePaths())
                .status(row.getStatus().name())
                .disputed(row.isDisputed())
                .acknowledgedAt(row.getAcknowledgedAt())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private ScScorecardResponse toScorecardResponse(ScSubcontractorScore score) {
        return ScScorecardResponse.builder()
                .uuid(score.getUuid())
                .organizationUuid(score.getOrganizationUuid())
                .packageUuid(score.getPackageUuid())
                .qualityScore(score.getQualityScore())
                .programmeScore(score.getProgrammeScore())
                .safetyScore(score.getSafetyScore())
                .commercialScore(score.getCommercialScore())
                .responsivenessScore(score.getResponsivenessScore())
                .totalScore(score.getTotalScore())
                .computedAt(score.getComputedAt())
                .build();
    }

    private SubcontractorClaim requireClaim(UUID claimUuid, Long projectId) {
        SubcontractorClaim claim = claimRepository.findByUuidAndCompanyId(claimUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.getProjectId().equals(projectId)) {
            throw new BadRequestException("Claim does not belong to this project");
        }
        return claim;
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
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
