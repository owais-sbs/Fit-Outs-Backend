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
import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.profitloss.application.PnlCalculationService;
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
import com.fitouts.subcontractor.api.ScRetentionReleaseRequest;
import com.fitouts.subcontractor.api.ScScorecardResponse;
import com.fitouts.subcontractor.api.ScMeasureClaimLineRequest;
import com.fitouts.subcontractor.domain.ScBackCharge;
import com.fitouts.subcontractor.domain.ScBackChargeRepository;
import com.fitouts.subcontractor.domain.ScBackChargeStatus;
import com.fitouts.subcontractor.domain.ScClaimLine;
import com.fitouts.subcontractor.domain.ScClaimLineRepository;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScInvoice;
import com.fitouts.subcontractor.domain.ScInvoiceRepository;
import com.fitouts.subcontractor.domain.ScInvoiceStatus;
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
import com.fitouts.completion.application.CommercialLifecycleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScWave7CommercialService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS);

    private static final Set<Role> FINANCE_ROLES = EnumSet.of(
            Role.ADMIN, Role.BUSINESS_OWNER, Role.FINANCE);

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
    private final ScClaimLineRepository claimLineRepository;
    private final ScInvoiceRepository invoiceRepository;
    private final ScPortalAccessService portalAccessService;
    private final ProjectService projectService;
    private final CommercialApprovalService commercialApprovalService;
    private final CommercialLifecycleService commercialLifecycleService;
    private final PnlCalculationService pnlCalculationService;

    @Transactional
    public SubcontractorClaim measureClaim(Long projectId, UUID claimUuid, ScMeasureClaimRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        SubcontractorClaim claim = requireClaim(claimUuid, projectId);
        if (claim.getStatus() != SubcontractorClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be measured");
        commercialLifecycleService.assertNotArchived(projectId);
        if (claim.getStatus() != SubcontractorClaimStatus.SUBMITTED
                && claim.getStatus() != SubcontractorClaimStatus.UNDER_REVIEW
                && claim.getStatus() != SubcontractorClaimStatus.APPROVED) {
            throw new BadRequestException("Only SUBMITTED / UNDER_REVIEW / APPROVED claims can be measured");
        }
        if (request == null) {
            throw new BadRequestException("Measurement values are required");
        }

        List<ScClaimLine> claimLines = claimLineRepository.findByClaimUuidOrderBySortOrderAsc(claim.getUuid());
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            java.util.Map<UUID, ScClaimLine> byId = new java.util.HashMap<>();
            for (ScClaimLine line : claimLines) {
                byId.put(line.getUuid(), line);
            }
            BigDecimal measuredQtyTotal = BigDecimal.ZERO;
            BigDecimal measuredValueTotal = BigDecimal.ZERO;
            for (ScMeasureClaimLineRequest lineReq : request.getLines()) {
                if (lineReq == null || lineReq.getClaimLineUuid() == null) {
                    continue;
                }
                ScClaimLine line = byId.get(lineReq.getClaimLineUuid());
                if (line == null) {
                    throw new BadRequestException("Claim line not found on this claim");
                }
                BigDecimal mq = lineReq.getMeasuredQty() != null ? lineReq.getMeasuredQty() : line.getClaimedQty();
                if (mq == null || mq.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("Measured quantity cannot be negative");
                }
                BigDecimal rate = line.getAwardRate() != null ? line.getAwardRate() : BigDecimal.ZERO;
                BigDecimal mv = mq.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                // Preserve claimed qty; only set measured fields
                line.setMeasuredQty(mq);
                line.setMeasuredValue(mv);
                line.setQsComment(trimToNull(lineReq.getQsComment()));
                claimLineRepository.save(line);
                measuredQtyTotal = measuredQtyTotal.add(mq);
                measuredValueTotal = measuredValueTotal.add(mv);
            }
            claim.setMeasuredQty(measuredQtyTotal);
            claim.setMeasuredValue(measuredValueTotal);
        } else {
            claim.setMeasuredQty(request.getMeasuredQty() != null ? request.getMeasuredQty() : claim.getClaimedQty());
            if (request.getMeasuredValue() != null) {
                claim.setMeasuredValue(request.getMeasuredValue());
            } else if (!claimLines.isEmpty()) {
                BigDecimal measuredValueTotal = BigDecimal.ZERO;
                BigDecimal measuredQtyTotal = BigDecimal.ZERO;
                for (ScClaimLine line : claimLines) {
                    BigDecimal mq = line.getClaimedQty() != null ? line.getClaimedQty() : BigDecimal.ZERO;
                    BigDecimal rate = line.getAwardRate() != null ? line.getAwardRate() : BigDecimal.ZERO;
                    BigDecimal mv = mq.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                    line.setMeasuredQty(mq);
                    line.setMeasuredValue(mv);
                    claimLineRepository.save(line);
                    measuredQtyTotal = measuredQtyTotal.add(mq);
                    measuredValueTotal = measuredValueTotal.add(mv);
                }
                claim.setMeasuredQty(measuredQtyTotal);
                claim.setMeasuredValue(measuredValueTotal);
            } else if (claim.getClaimedValue() != null) {
                claim.setMeasuredValue(claim.getClaimedValue());
            } else {
                claim.setMeasuredValue(null);
            }
        }
        if (claim.getMeasuredValue() == null) {
            BigDecimal fromLines = claimLineRepository.findByClaimUuidOrderBySortOrderAsc(claim.getUuid()).stream()
                    .map(l -> l.getMeasuredValue() != null ? l.getMeasuredValue() : l.getClaimedValue())
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (fromLines.compareTo(BigDecimal.ZERO) > 0) {
                claim.setMeasuredValue(fromLines);
            } else if (claim.getClaimedValue() != null) {
                claim.setMeasuredValue(claim.getClaimedValue());
            }
        }
        claim.setMeasuredBy(principal.getAccountId());
        claim.setMeasuredAt(OffsetDateTime.now());
        claim.setStatus(SubcontractorClaimStatus.MEASURED);
        return claimRepository.save(claim);
    }

    @Transactional
    public ScPaymentCertificateResponse certifyClaim(Long projectId, UUID claimUuid, ScCertifyClaimRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
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
            certifiedValue = claim.getClaimedValue();
        }
        if (certifiedValue == null) {
            certifiedValue = claimLineRepository.findByClaimUuidOrderBySortOrderAsc(claim.getUuid()).stream()
                    .map(l -> l.getMeasuredValue() != null ? l.getMeasuredValue()
                            : (l.getClaimedValue() != null ? l.getClaimedValue() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (certifiedValue.compareTo(BigDecimal.ZERO) <= 0) {
                certifiedValue = null;
            }
            throw new BadRequestException(
                    "certifiedValue is required — measure the claim with a value first, or pass certifiedValue");
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
        BigDecimal otherDeductions = request != null && request.getOtherDeductions() != null
                ? request.getOtherDeductions() : BigDecimal.ZERO;
        BigDecimal netPayable = certifiedValue.subtract(retentionHeld).subtract(backCharges)
                .subtract(otherDeductions).max(BigDecimal.ZERO);

        ScPaymentCertificate cert = new ScPaymentCertificate();
        cert.setClaimUuid(claim.getUuid());
        cert.setPackageUuid(pkg.getUuid());
        cert.setProjectId(claim.getProjectId());
        cert.setCompanyId(claim.getCompanyId());
        cert.setOrganizationUuid(orgUuid);
        cert.setCertifiedValue(certifiedValue);
        cert.setRetentionHeld(retentionHeld);
        cert.setBackChargesApplied(backCharges);
        cert.setOtherDeductions(otherDeductions);
        cert.setNetPayable(netPayable);
        cert.setStatus(ScPaymentCertificateStatus.DRAFT);
        cert.setCertificateDate(LocalDate.now());
        if (request != null) {
            cert.setAccountingRef(trimToNull(request.getAccountingRef()));
        }
        final ScPaymentCertificate savedCert = certificateRepository.save(cert);
        savedCert.setCertificateNumber("PC-" + savedCert.getUuid().toString().substring(0, 8).toUpperCase());
        certificateRepository.save(savedCert);
        final UUID savedCertUuid = savedCert.getUuid();

        // Copy measured → certified on claim lines (certified qty defaults to measured)
        for (ScClaimLine line : claimLineRepository.findByClaimUuidOrderBySortOrderAsc(claim.getUuid())) {
            BigDecimal cq = line.getMeasuredQty() != null ? line.getMeasuredQty() : line.getClaimedQty();
            BigDecimal rate = line.getAwardRate() != null ? line.getAwardRate() : BigDecimal.ZERO;
            line.setCertifiedQty(cq);
            line.setCertifiedValue(cq != null ? cq.multiply(rate).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            claimLineRepository.save(line);
        }

        // Module 23: optional SC certificate matrix — if a band matches, hold as DRAFT until approved
        var runOpt = commercialApprovalService.startRun(
                CommercialEventType.SC_CERTIFICATE,
                savedCertUuid,
                claim.getProjectId(),
                netPayable,
                principal.getAccountId());
        if (runOpt.isPresent()) {
            claim.setCertifiedValue(certifiedValue);
            claim.setCertificateUuid(savedCertUuid);
            claimRepository.save(claim);
            return toCertificateResponse(savedCert);
        }

        issueCertificate(savedCert, claim, principal.getAccountId(), applicableStatuses);
        // No matrix configured: QS certification is the gate → PAYABLE (ready for SC invoice).
        // When an SC_CERTIFICATE matrix exists, approval handler marks PAYABLE after all steps.
        issueCertificate(savedCert, claim, principal.getAccountId(), applicableStatuses, true);
        return toCertificateResponse(savedCert);
    }

    /** Finalize DRAFT certificate after matrix approval (or immediate when no matrix). */
    @Transactional
    public void issueCertificateAfterMatrix(UUID certificateUuid) {
        ScPaymentCertificate cert = certificateRepository.findById(certificateUuid)
                .orElseThrow(() -> new NotFoundException("Payment certificate not found"));
        if (cert.getStatus() != ScPaymentCertificateStatus.DRAFT) {
            return;
        }
        SubcontractorClaim claim = claimRepository.findById(cert.getClaimUuid())
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        List<ScBackChargeStatus> applicableStatuses = List.of(
                ScBackChargeStatus.OPEN, ScBackChargeStatus.ACKNOWLEDGED);
        issueCertificate(cert, claim, null, applicableStatuses);
        commercialLifecycleService.assertNotArchived(claim.getProjectId());
        issueCertificate(cert, claim, null, applicableStatuses, true);
    }

    @Transactional
    public void cancelCertificateAfterMatrixReject(UUID certificateUuid, String comment) {
        ScPaymentCertificate cert = certificateRepository.findById(certificateUuid).orElse(null);
        if (cert == null || cert.getStatus() != ScPaymentCertificateStatus.DRAFT) {
            return;
        }
        SubcontractorClaim claim = claimRepository.findById(cert.getClaimUuid()).orElse(null);
        commercialLifecycleService.assertNotArchived(cert.getProjectId());
        if (claim != null && cert.getUuid().equals(claim.getCertificateUuid())) {
            claim.setCertificateUuid(null);
            claim.setCertifiedValue(null);
            claimRepository.save(claim);
        }
        certificateRepository.delete(cert);
    }

    /**
     * @param asPayable true when Module 23 matrix has completed (Finance/Director already approved);
     *                  false when QS certifies with no matrix — leaves ISSUED until Finance marks PAYABLE.
     */
    private void issueCertificate(
            ScPaymentCertificate cert,
            SubcontractorClaim claim,
            Long decidedBy,
            List<ScBackChargeStatus> applicableStatuses) {
        final UUID savedCertUuid = cert.getUuid();
        UUID orgUuid = cert.getOrganizationUuid();
        BigDecimal retentionHeld = cert.getRetentionHeld() != null ? cert.getRetentionHeld() : BigDecimal.ZERO;

        cert.setStatus(ScPaymentCertificateStatus.ISSUED);
            List<ScBackChargeStatus> applicableStatuses,
            boolean asPayable) {
        BigDecimal retentionPct = BigDecimal.ZERO;
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(cert.getPackageUuid(), cert.getCompanyId())
                .orElse(null);
        if (pkg != null && pkg.getRetentionPct() != null) {
            retentionPct = pkg.getRetentionPct();
        }

        cert.setStatus(asPayable ? ScPaymentCertificateStatus.PAYABLE : ScPaymentCertificateStatus.ISSUED);
        if (cert.getCertificateDate() == null) {
            cert.setCertificateDate(LocalDate.now());
        certificateRepository.save(cert);

        if (orgUuid != null && retentionHeld.compareTo(BigDecimal.ZERO) > 0) {
            ScRetentionLedger ledger = new ScRetentionLedger();
            ledger.setPackageUuid(cert.getPackageUuid());
            ledger.setOrganizationUuid(orgUuid);
            ledger.setCompanyId(cert.getCompanyId());
            ledger.setProjectId(cert.getProjectId());
            ledger.setAmountHeld(retentionHeld);
            ledger.setAmountReleased(BigDecimal.ZERO);
            ledger.setRetentionPct(retentionPct);
            ledger.setCertifiedValue(cert.getCertifiedValue());
            ledger.setStatus(ScRetentionStatus.HELD);
            ledger.setCertificateUuid(savedCertUuid);
            retentionLedgerRepository.save(ledger);
        }

        if (orgUuid != null) {
            backChargeRepository
                    .findByPackageUuidAndOrganizationUuidAndStatusIn(
                            cert.getPackageUuid(), orgUuid, applicableStatuses)
                    .forEach(charge -> {
                        charge.setStatus(ScBackChargeStatus.APPLIED);
                        charge.setAppliedToCertificateUuid(savedCertUuid);
                        backChargeRepository.save(charge);
                    });
        }

        claim.setCertifiedValue(cert.getCertifiedValue());
        claim.setCertificateUuid(savedCertUuid);
        claim.setStatus(SubcontractorClaimStatus.CERTIFIED);
        if (decidedBy != null) {
            claim.setDecidedBy(decidedBy);
        }
        claim.setDecidedAt(OffsetDateTime.now());
        claimRepository.save(claim);

        pnlCalculationService.recalculateSafe(cert.getProjectId(), cert.getCompanyId());
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
    public ScPaymentCertificateResponse markCertificatePayable(Long projectId, UUID certificateUuid) {
        commercialLifecycleService.assertNotArchived(projectId);
        requireFinance();
        ScPaymentCertificate cert = certificateRepository.findById(certificateUuid)
        if (!cert.getProjectId().equals(projectId) || !cert.getCompanyId().equals(requireCompany())) {
            throw new ForbiddenException("Certificate not in this project");
        if (cert.getStatus() != ScPaymentCertificateStatus.ISSUED
                && cert.getStatus() != ScPaymentCertificateStatus.DRAFT) {
            throw new BadRequestException("Only ISSUED or DRAFT certificates can become PAYABLE");
        if (cert.getStatus() == ScPaymentCertificateStatus.DRAFT) {
            // Finalize retention/back-charges if still draft without matrix completion path
            SubcontractorClaim claim = claimRepository.findById(cert.getClaimUuid())
                    .orElseThrow(() -> new NotFoundException("Claim not found"));
            List<ScBackChargeStatus> applicableStatuses = List.of(
                    ScBackChargeStatus.OPEN, ScBackChargeStatus.ACKNOWLEDGED);
            issueCertificate(cert, claim, null, applicableStatuses, true);
            return toCertificateResponse(certificateRepository.findById(certificateUuid).orElse(cert));
        cert.setStatus(ScPaymentCertificateStatus.PAYABLE);
        return toCertificateResponse(certificateRepository.save(cert));
    }

    @Transactional
        if (claim.getStatus() != SubcontractorClaimStatus.CERTIFIED
                && claim.getStatus() != SubcontractorClaimStatus.PAID) {
            throw new BadRequestException("Only CERTIFIED claims can be marked paid via certificate");
        if (cert.getStatus() != ScPaymentCertificateStatus.PAYABLE
                && cert.getStatus() != ScPaymentCertificateStatus.ISSUED) {
            throw new BadRequestException("Certificate must be PAYABLE before recording payment");
        if (cert.getStatus() == ScPaymentCertificateStatus.ISSUED) {
            cert.setStatus(ScPaymentCertificateStatus.PAYABLE);
        cert.setPaidAmount(cert.getNetPayable());
        AuthPrincipal principal = requireAuthenticated();
        cert.setPaidBy(principal.getAccountId());
        if (StringUtils.hasText(accountingRef)) {
            cert.setAccountingRef(accountingRef.trim());
        }
        certificateRepository.save(cert);
        claim.setStatus(SubcontractorClaimStatus.PAID);
        // Sync linked invoices so SC "Total received" reflects payment
        for (ScInvoice inv : invoiceRepository.findByCertificateUuidAndCompanyId(cert.getUuid(), cert.getCompanyId())) {
            if (inv.getStatus() == ScInvoiceStatus.REJECTED || inv.getStatus() == ScInvoiceStatus.PAID) {
                continue;
            }
            inv.setStatus(ScInvoiceStatus.PAID);
            inv.setPaidAt(OffsetDateTime.now());
            if (StringUtils.hasText(accountingRef)) {
                inv.setPaymentReference(accountingRef.trim());
            } else if (StringUtils.hasText(cert.getAccountingRef())) {
                inv.setPaymentReference(cert.getAccountingRef());
            inv.setDecidedBy(principal.getAccountId());
            inv.setDecidedAt(OffsetDateTime.now());
            invoiceRepository.save(inv);
        }
        // Claim primary status stays CERTIFIED — payment is downstream on the certificate
        claim.setStatus(SubcontractorClaimStatus.CERTIFIED);
        SubcontractorClaim saved = claimRepository.save(claim);
        pnlCalculationService.recalculateSafe(cert.getProjectId(), cert.getCompanyId());
        return saved;
    }

    @Transactional
    public ScRetentionLedgerResponse releaseRetention(Long projectId, UUID retentionUuid, ScRetentionReleaseRequest request) {
        commercialLifecycleService.assertNotArchived(projectId);
        AuthPrincipal principal = requireFinance();
        requireProject(projectId);
        ScRetentionLedger row = retentionLedgerRepository.findById(retentionUuid)
                .orElseThrow(() -> new NotFoundException("Retention ledger entry not found"));
        if (!row.getProjectId().equals(projectId) || !row.getCompanyId().equals(requireCompany())) {
            throw new ForbiddenException("Retention entry not in this project");
        }
        if (row.getStatus() == ScRetentionStatus.RELEASED) {
            throw new BadRequestException("Retention already fully released");
        }
        BigDecimal held = row.getAmountHeld() != null ? row.getAmountHeld() : BigDecimal.ZERO;
        BigDecimal already = row.getAmountReleased() != null ? row.getAmountReleased() : BigDecimal.ZERO;
        BigDecimal outstanding = held.subtract(already).max(BigDecimal.ZERO);
        BigDecimal releaseAmt = request != null && request.getAmountReleased() != null
                ? request.getAmountReleased() : outstanding;
        if (releaseAmt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Release amount must be positive");
        }
        if (releaseAmt.compareTo(outstanding) > 0) {
            throw new BadRequestException("Release amount exceeds outstanding retention");
        }
        row.setAmountReleased(already.add(releaseAmt));
        row.setReleaseDate(request != null && request.getReleaseDate() != null
                ? request.getReleaseDate() : LocalDate.now());
        row.setReleasedAt(OffsetDateTime.now());
        row.setReleasedBy(principal.getAccountId());
        if (StringUtils.hasText(request != null ? request.getNotes() : null)) {
            row.setNotes(request.getNotes().trim());
        }
        if (row.getAmountReleased().compareTo(held) >= 0) {
            row.setStatus(ScRetentionStatus.RELEASED);
        } else {
            row.setStatus(ScRetentionStatus.APPROVED_FOR_RELEASE);
        }
        return toRetentionResponse(retentionLedgerRepository.save(row));
    }

    @Transactional
    public ScRetentionLedgerResponse markRetentionEligible(Long projectId, UUID retentionUuid) {
        commercialLifecycleService.assertNotArchived(projectId);
        requireFinance();
        requireProject(projectId);
        ScRetentionLedger row = retentionLedgerRepository.findById(retentionUuid)
                .orElseThrow(() -> new NotFoundException("Retention ledger entry not found"));
        if (!row.getProjectId().equals(projectId)) {
            throw new ForbiddenException("Retention entry not in this project");
        }
        if (row.getStatus() != ScRetentionStatus.HELD) {
            throw new BadRequestException("Only HELD retention can be marked eligible");
        }
        row.setStatus(ScRetentionStatus.ELIGIBLE_FOR_RELEASE);
        return toRetentionResponse(retentionLedgerRepository.save(row));
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
        commercialLifecycleService.assertNotArchived(projectId);
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
        commercialLifecycleService.assertNotArchived(charge.getProjectId());
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
                        .map(claim -> {
                            String paymentStatus = null;
                            if (claim.getCertificateUuid() != null) {
                                paymentStatus = certificateRepository.findById(claim.getCertificateUuid())
                                        .map(c -> c.getStatus().name())
                                        .orElse(null);
                            }
                            return ScClaimStatusTrackerResponse.builder()
                                .claimUuid(claim.getUuid())
                                .packageUuid(pkg.getUuid())
                                .packageName(pkg.getName())
                                .projectId(claim.getProjectId())
                                .status(claim.getStatus().name())
                                .status(claim.getStatus() == SubcontractorClaimStatus.PAID
                                        ? SubcontractorClaimStatus.CERTIFIED.name()
                                        : claim.getStatus().name())
                                .claimedQty(claim.getClaimedQty())
                                .measuredQty(claim.getMeasuredQty())
                                .measuredValue(claim.getMeasuredValue())
                                .certifiedValue(claim.getCertifiedValue())
                                .certificateUuid(claim.getCertificateUuid())
                                .paymentStatus(paymentStatus)
                                .submittedAt(claim.getSubmittedAt())
                                .measuredAt(claim.getMeasuredAt())
                                .build();
                        }))
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
        UUID invoiceUuid = null;
        if (invoiceRepository != null) {
            invoiceUuid = invoiceRepository.findByCertificateUuidAndCompanyId(cert.getUuid(), cert.getCompanyId())
                    .stream()
                    .findFirst()
                    .map(ScInvoice::getUuid)
                    .orElse(null);
        }
        String claimNumber = null;
        if (cert.getClaimUuid() != null) {
            claimNumber = claimRepository.findById(cert.getClaimUuid())
                    .map(SubcontractorClaim::getClaimNumber)
                    .orElse(null);
        }
        String packageName = packageRepository.findByUuidAndCompanyId(cert.getPackageUuid(), cert.getCompanyId())
                .map(SubcontractorPackage::getName)
                .orElse(null);
        return ScPaymentCertificateResponse.builder()
                .uuid(cert.getUuid())
                .claimUuid(cert.getClaimUuid())
                .packageUuid(cert.getPackageUuid())
                .projectId(cert.getProjectId())
                .organizationUuid(cert.getOrganizationUuid())
                .certifiedValue(cert.getCertifiedValue())
                .retentionHeld(cert.getRetentionHeld())
                .backChargesApplied(cert.getBackChargesApplied())
                .otherDeductions(cert.getOtherDeductions())
                .netPayable(cert.getNetPayable())
                .status(cert.getStatus().name())
                .certificateNumber(cert.getCertificateNumber())
                .certificateDate(cert.getCertificateDate())
                .paidDate(cert.getPaidDate())
                .paidAmount(cert.getPaidAmount())
                .accountingRef(cert.getAccountingRef())
                .paymentNotes(cert.getPaymentNotes())
                .createdAt(cert.getCreatedAt())
                .packageName(packageName)
                .claimNumber(claimNumber)
                .invoiceUuid(invoiceUuid)
                .build();
    }

    private ScRetentionLedgerResponse toRetentionResponse(ScRetentionLedger row) {
        BigDecimal held = row.getAmountHeld() != null ? row.getAmountHeld() : BigDecimal.ZERO;
        BigDecimal released = row.getAmountReleased() != null ? row.getAmountReleased() : BigDecimal.ZERO;
        String packageName = packageRepository.findByUuidAndCompanyId(row.getPackageUuid(), row.getCompanyId())
                .map(SubcontractorPackage::getName)
                .orElse(null);
        return ScRetentionLedgerResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .organizationUuid(row.getOrganizationUuid())
                .projectId(row.getProjectId())
                .amountHeld(row.getAmountHeld())
                .amountHeld(held)
                .amountReleased(released)
                .outstandingBalance(held.subtract(released).max(BigDecimal.ZERO))
                .retentionPct(row.getRetentionPct())
                .certifiedValue(row.getCertifiedValue())
                .releaseDate(row.getReleaseDate())
                .defectLiabilityEnd(row.getDefectLiabilityEnd())
                .status(row.getStatus().name())
                .certificateUuid(row.getCertificateUuid())
                .notes(row.getNotes())
                .createdAt(row.getCreatedAt())
                .packageName(packageName)
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

    private AuthPrincipal requireFinance() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Finance access required");
        }
        for (Role role : FINANCE_ROLES) {
            if (principal.getRoles().contains(role)) {
                return principal;
            }
        }
        // Directors / PMs / QS who certified may also record payable/paid in small orgs
        for (Role role : STAFF_ROLES) {
            if (principal.getRoles().contains(role)) {
                return principal;
            }
        }
        throw new ForbiddenException("Finance or commercial staff access required");
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
