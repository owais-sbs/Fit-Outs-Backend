package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.snag.domain.Snag;
import com.fitouts.snag.domain.SnagRepository;
import com.fitouts.subcontractor.api.ClaimRejectRequest;
import com.fitouts.subcontractor.api.ScBoqLineView;
import com.fitouts.subcontractor.api.ScInvoiceRequestBody;
import com.fitouts.subcontractor.api.ScInvoiceResponse;
import com.fitouts.subcontractor.api.ScMarkPaidRequest;
import com.fitouts.subcontractor.api.ScSiteReportRequestBody;
import com.fitouts.subcontractor.api.ScSiteReportResponse;
import com.fitouts.subcontractor.api.ScSnagPortalResponse;
import com.fitouts.subcontractor.api.ScVariationRequestBody;
import com.fitouts.subcontractor.api.ScVariationResponse;
import com.fitouts.subcontractor.domain.ScBankDetailRepository;
import com.fitouts.subcontractor.domain.ScBankVerificationStatus;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScInvoice;
import com.fitouts.subcontractor.domain.ScInvoiceRepository;
import com.fitouts.subcontractor.domain.ScInvoiceStatus;
import com.fitouts.subcontractor.domain.ScSiteReport;
import com.fitouts.subcontractor.domain.ScSiteReportRepository;
import com.fitouts.subcontractor.domain.ScSiteReportStatus;
import com.fitouts.subcontractor.domain.ScSiteReportType;
import com.fitouts.subcontractor.domain.ScVariationRequest;
import com.fitouts.subcontractor.domain.ScVariationRequestRepository;
import com.fitouts.subcontractor.domain.ScVariationStatus;
import com.fitouts.subcontractor.domain.SubcontractorClaim;
import com.fitouts.subcontractor.domain.SubcontractorClaimRepository;
import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubcontractorPortalService {

    private final ScVariationRequestRepository variationRepository;
    private final ScSiteReportRepository siteReportRepository;
    private final ScInvoiceRepository invoiceRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final SubcontractorClaimRepository claimRepository;
    private final BoqLineRepository boqLineRepository;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final ScPortalAccessService portalAccessService;
    private final ScBankDetailRepository bankDetailRepository;
    private final ScCompanyProfileRepository profileRepository;
    private final SnagRepository snagRepository;

    // ── Snags ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScSnagPortalResponse> listMySnags() {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireExecutionAccess(principal);
        UUID companyId = requireCompany();
        List<SubcontractorPackage> packages = myAppointedPackages();
        List<Long> projectIds = packages.stream()
                .map(SubcontractorPackage::getProjectId)
                .distinct()
                .toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return snagRepository
                .findByAssigneeAccountIdAndProjectIdInAndCompanyIdOrderByCreatedAtDesc(
                        principal.getAccountId(), projectIds, companyId)
                .stream()
                .map(this::toSnagResponse)
                .toList();
    }

    // ── Variations ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScVariationResponse> myVariations() {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireCommercialAccess(principal);
        return listAllVariationsForPackages(myAppointedPackages());
    }

    @Transactional(readOnly = true)
    public List<ScVariationResponse> listVariationsForProject(Long projectId) {
        requireStaff();
        requireProject(projectId);
        return variationRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, requireCompany())
                .stream().map(this::toVariationResponse).toList();
    }

    @Transactional
    public ScVariationResponse createVariation(UUID packageUuid, ScVariationRequestBody body) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireCommercialAccess(principal);
        SubcontractorPackage pkg = requireAppointedPackage(principal, packageUuid);
        if (body == null || !StringUtils.hasText(body.getTitle())) {
            throw new BadRequestException("title is required");
        }
        ScVariationRequest row = new ScVariationRequest();
        row.setPackageUuid(pkg.getUuid());
        row.setProjectId(pkg.getProjectId());
        row.setCompanyId(pkg.getCompanyId());
        row.setTitle(body.getTitle().trim());
        row.setDescription(trimToNull(body.getDescription()));
        row.setQtyDelta(body.getQtyDelta());
        row.setUnit(trimToNull(body.getUnit()));
        row.setEstimatedCost(body.getEstimatedCost());
        row.setStatus(ScVariationStatus.DRAFT);
        return toVariationResponse(variationRepository.save(row));
    }

    @Transactional
    public ScVariationResponse submitVariation(UUID uuid) {
        AuthPrincipal principal = requireAuthenticated();
        ScVariationRequest row = requireVariation(uuid);
        requireAppointedPackage(principal, row.getPackageUuid());
        if (row.getStatus() != ScVariationStatus.DRAFT && row.getStatus() != ScVariationStatus.REJECTED) {
            throw new BadRequestException("Only DRAFT or REJECTED variations can be submitted");
        }
        row.setStatus(ScVariationStatus.SUBMITTED);
        row.setSubmittedBy(principal.getAccountId());
        row.setSubmittedAt(OffsetDateTime.now());
        row.setDecidedBy(null);
        row.setDecidedAt(null);
        row.setReason(null);
        return toVariationResponse(variationRepository.save(row));
    }

    @Transactional
    public ScVariationResponse uploadVariationAttachment(UUID uuid, MultipartFile file) {
        AuthPrincipal principal = requireAuthenticated();
        ScVariationRequest row = requireVariation(uuid);
        requireAppointedPackage(principal, row.getPackageUuid());
        if (row.getStatus() != ScVariationStatus.DRAFT && row.getStatus() != ScVariationStatus.REJECTED) {
            throw new BadRequestException("Attachments only on DRAFT or REJECTED variations");
        }
        row.setAttachmentPaths(appendPath(row.getAttachmentPaths(), storeFile(file, row)));
        return toVariationResponse(variationRepository.save(row));
    }

    @Transactional
    public ScVariationResponse approveVariation(Long projectId, UUID uuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        ScVariationRequest row = requireSubmittedVariation(uuid, projectId);
        row.setStatus(ScVariationStatus.APPROVED);
        row.setDecidedBy(principal.getAccountId());
        row.setDecidedAt(OffsetDateTime.now());
        row.setReason(null);
        return toVariationResponse(variationRepository.save(row));
    }

    @Transactional
    public ScVariationResponse rejectVariation(Long projectId, UUID uuid, ClaimRejectRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        ScVariationRequest row = requireSubmittedVariation(uuid, projectId);
        row.setStatus(ScVariationStatus.REJECTED);
        row.setDecidedBy(principal.getAccountId());
        row.setDecidedAt(OffsetDateTime.now());
        row.setReason(request.getReason().trim());
        return toVariationResponse(variationRepository.save(row));
    }

    // ── Site reports (delay / issue / material) ──────────────────────────────

    @Transactional(readOnly = true)
    public List<ScSiteReportResponse> mySiteReports(ScSiteReportType type) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireExecutionAccess(principal);
        UUID companyId = requireCompany();
        List<ScSiteReport> rows = type == null
                ? siteReportRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : siteReportRepository.findByCompanyIdAndReportTypeOrderByCreatedAtDesc(companyId, type);
        Set<Long> projectIds = myAppointedPackages().stream()
                .map(SubcontractorPackage::getProjectId).collect(Collectors.toSet());
        return rows.stream()
                .filter(r -> Objects.equals(r.getRaisedBy(), principal.getAccountId())
                        || projectIds.contains(r.getProjectId()))
                .map(this::toSiteReportResponse)
                .toList();
    }

    @Transactional
    public ScSiteReportResponse createSiteReport(ScSiteReportRequestBody body) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireExecutionAccess(principal);
        if (body == null || body.getReportType() == null) {
            throw new BadRequestException("reportType is required");
        }
        if (!StringUtils.hasText(body.getTitle())) {
            throw new BadRequestException("title is required");
        }
        if (body.getProjectId() == null) {
            throw new BadRequestException("projectId is required");
        }
        assertProjectAppointed(principal, body.getProjectId());
        if (body.getPackageUuid() != null) {
            requireAppointedPackage(principal, body.getPackageUuid());
        }
        ScSiteReport row = new ScSiteReport();
        row.setProjectId(body.getProjectId());
        row.setCompanyId(requireCompany());
        row.setPackageUuid(body.getPackageUuid());
        row.setReportType(body.getReportType());
        row.setTitle(body.getTitle().trim());
        row.setDescription(trimToNull(body.getDescription()));
        row.setDelayReasonCode(trimToNull(body.getDelayReasonCode()));
        row.setExpectedDate(body.getExpectedDate());
        row.setDeliveredDate(body.getDeliveredDate());
        row.setMaterialName(trimToNull(body.getMaterialName()));
        row.setQuantity(body.getQuantity());
        row.setUnit(trimToNull(body.getUnit()));
        row.setStatus(ScSiteReportStatus.OPEN);
        row.setRaisedBy(principal.getAccountId());
        return toSiteReportResponse(siteReportRepository.save(row));
    }

    @Transactional
    public ScSiteReportResponse acknowledgeSiteReport(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        ScSiteReport row = requireSiteReport(uuid, projectId);
        row.setStatus(ScSiteReportStatus.ACKNOWLEDGED);
        row.setAcknowledgedBy(principal.getAccountId());
        row.setAcknowledgedAt(OffsetDateTime.now());
        return toSiteReportResponse(siteReportRepository.save(row));
    }

    @Transactional
    public ScSiteReportResponse resolveSiteReport(Long projectId, UUID uuid, String resolutionNotes) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        ScSiteReport row = requireSiteReport(uuid, projectId);
        row.setStatus(ScSiteReportStatus.RESOLVED);
        row.setResolvedBy(principal.getAccountId());
        row.setResolvedAt(OffsetDateTime.now());
        row.setResolutionNotes(trimToNull(resolutionNotes));
        return toSiteReportResponse(siteReportRepository.save(row));
    }

    // ── Invoices / payments ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScInvoiceResponse> myInvoices() {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireCommercialAccess(principal);
        return listInvoicesForPackages(myAppointedPackages());
    }

    @Transactional
    public ScInvoiceResponse createInvoice(ScInvoiceRequestBody body) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireCommercialAccess(principal);
        assertBankReadyForPayment(principal);
        if (body == null || body.getPackageUuid() == null) {
            throw new BadRequestException("packageUuid is required");
        }
        SubcontractorPackage pkg = requireAppointedPackage(principal, body.getPackageUuid());
        if (body.getClaimUuid() != null) {
            SubcontractorClaim claim = claimRepository.findByUuidAndCompanyId(body.getClaimUuid(), pkg.getCompanyId())
                    .orElseThrow(() -> new NotFoundException("Claim not found"));
            if (!claim.getPackageUuid().equals(pkg.getUuid())) {
                throw new BadRequestException("Claim does not belong to this package");
            }
            if (claim.getStatus() != SubcontractorClaimStatus.APPROVED) {
                throw new BadRequestException("Invoice can only link to APPROVED claims");
            }
        }
        ScInvoice row = new ScInvoice();
        row.setPackageUuid(pkg.getUuid());
        row.setProjectId(pkg.getProjectId());
        row.setCompanyId(pkg.getCompanyId());
        row.setClaimUuid(body.getClaimUuid());
        row.setInvoiceNumber(trimToNull(body.getInvoiceNumber()));
        row.setAmount(body.getAmount() != null ? body.getAmount() : BigDecimal.ZERO);
        row.setTaxAmount(body.getTaxAmount() != null ? body.getTaxAmount() : BigDecimal.ZERO);
        row.setCurrency(StringUtils.hasText(body.getCurrency()) ? body.getCurrency().trim() : "AED");
        row.setNotes(trimToNull(body.getNotes()));
        row.setStatus(ScInvoiceStatus.DRAFT);
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    @Transactional
    public ScInvoiceResponse submitInvoice(UUID uuid) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireCommercialAccess(principal);
        assertBankReadyForPayment(principal);
        ScInvoice row = requireInvoice(uuid);
        requireAppointedPackage(principal, row.getPackageUuid());
        if (row.getStatus() != ScInvoiceStatus.DRAFT && row.getStatus() != ScInvoiceStatus.REJECTED) {
            throw new BadRequestException("Only DRAFT or REJECTED invoices can be submitted");
        }
        if (row.getAmount() == null || row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("amount must be greater than zero");
        }
        row.setStatus(ScInvoiceStatus.SUBMITTED);
        row.setSubmittedBy(principal.getAccountId());
        row.setSubmittedAt(OffsetDateTime.now());
        row.setDecidedBy(null);
        row.setDecidedAt(null);
        row.setReason(null);
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    @Transactional
    public ScInvoiceResponse uploadInvoiceAttachment(UUID uuid, MultipartFile file) {
        AuthPrincipal principal = requireAuthenticated();
        ScInvoice row = requireInvoice(uuid);
        requireAppointedPackage(principal, row.getPackageUuid());
        if (row.getStatus() != ScInvoiceStatus.DRAFT && row.getStatus() != ScInvoiceStatus.REJECTED) {
            throw new BadRequestException("Attachments only on DRAFT or REJECTED invoices");
        }
        row.setAttachmentPaths(appendPath(row.getAttachmentPaths(), storeFile(file, row)));
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    @Transactional
    public ScInvoiceResponse approveInvoice(Long projectId, UUID uuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        ScInvoice row = requireSubmittedInvoice(uuid, projectId);
        row.setStatus(ScInvoiceStatus.APPROVED);
        row.setDecidedBy(principal.getAccountId());
        row.setDecidedAt(OffsetDateTime.now());
        row.setReason(null);
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    @Transactional
    public ScInvoiceResponse rejectInvoice(Long projectId, UUID uuid, ClaimRejectRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        ScInvoice row = requireSubmittedInvoice(uuid, projectId);
        row.setStatus(ScInvoiceStatus.REJECTED);
        row.setDecidedBy(principal.getAccountId());
        row.setDecidedAt(OffsetDateTime.now());
        row.setReason(request.getReason().trim());
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    @Transactional
    public ScInvoiceResponse markInvoicePaid(Long projectId, UUID uuid, ScMarkPaidRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        ScInvoice row = requireInvoice(uuid);
        if (!row.getProjectId().equals(projectId)) {
            throw new BadRequestException("Invoice does not belong to this project");
        }
        if (row.getStatus() != ScInvoiceStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED invoices can be marked paid");
        }
        row.setStatus(ScInvoiceStatus.PAID);
        row.setPaidAt(OffsetDateTime.now());
        row.setPaymentReference(request != null ? trimToNull(request.getPaymentReference()) : null);
        row.setDecidedBy(principal.getAccountId());
        row.setDecidedAt(OffsetDateTime.now());
        return toInvoiceResponse(invoiceRepository.save(row));
    }

    // ── BOQ view ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScBoqLineView> myBoqLines(Long projectIdFilter) {
        AuthPrincipal principal = requireScPortalUser();
        portalAccessService.requireTenderingAccess(principal);
        List<SubcontractorPackage> packages = myAppointedPackages();
        if (projectIdFilter != null) {
            packages = packages.stream()
                    .filter(p -> Objects.equals(p.getProjectId(), projectIdFilter))
                    .toList();
        }
        List<ScBoqLineView> lines = new ArrayList<>();
        for (SubcontractorPackage pkg : packages) {
            lines.add(buildBoqLineView(pkg));
        }
        lines.sort(Comparator.comparing(ScBoqLineView::getProjectName, Comparator.nullsLast(String::compareTo))
                .thenComparing(ScBoqLineView::getSectionCode, Comparator.nullsLast(String::compareTo)));
        return lines;
    }

    @Transactional(readOnly = true)
    public List<ScVariationResponse> pendingVariationsForInbox() {
        requirePmOrAdmin();
        return variationRepository
                .findByCompanyIdAndStatusOrderBySubmittedAtDesc(requireCompany(), ScVariationStatus.SUBMITTED)
                .stream().map(this::toVariationResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ScInvoiceResponse> pendingInvoicesForInbox() {
        requirePmOrAdmin();
        return invoiceRepository
                .findByCompanyIdAndStatusOrderBySubmittedAtDesc(requireCompany(), ScInvoiceStatus.SUBMITTED)
                .stream().map(this::toInvoiceResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<SubcontractorPackage> myAppointedPackages() {
        AuthPrincipal principal = requireAuthenticated();
        return packageRepository.findByAppointedAccountIdAndCompanyIdOrderByCreatedAtDesc(
                principal.getAccountId(), requireCompany());
    }

    private List<ScVariationResponse> listVariationsForPackages(List<SubcontractorPackage> packages) {
        return listAllVariationsForPackages(packages);
    }

    private List<ScVariationResponse> listAllVariationsForPackages(List<SubcontractorPackage> packages) {
        UUID companyId = requireCompany();
        List<ScVariationResponse> result = new ArrayList<>();
        for (SubcontractorPackage pkg : packages) {
            variationRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(pkg.getUuid(), companyId)
                    .forEach(v -> result.add(toVariationResponse(v)));
        }
        result.sort(Comparator.comparing(ScVariationResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private List<ScInvoiceResponse> listInvoicesForPackages(List<SubcontractorPackage> packages) {
        List<ScInvoiceResponse> result = new ArrayList<>();
        UUID companyId = requireCompany();
        for (SubcontractorPackage pkg : packages) {
            invoiceRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(pkg.getUuid(), companyId)
                    .forEach(i -> result.add(toInvoiceResponse(i)));
        }
        result.sort(Comparator.comparing(ScInvoiceResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private ScBoqLineView buildBoqLineView(SubcontractorPackage pkg) {
        BoqLine line = pkg.getBoqLineId() != null
                ? boqLineRepository.findById(pkg.getBoqLineId()).orElse(null) : null;
        Project project = resolveProject(pkg.getProjectId());
        BigDecimal planned = line != null && line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
        BigDecimal approved = sumApprovedQty(pkg.getUuid());
        BigDecimal remaining = planned.subtract(approved).max(BigDecimal.ZERO);
        return ScBoqLineView.builder()
                .boqLineId(line != null ? line.getId() : null)
                .packageUuid(pkg.getUuid())
                .projectId(pkg.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .packageName(pkg.getName())
                .sectionCode(pkg.getBoqSectionCode())
                .description(line != null ? line.getDescription() : pkg.getName())
                .unit(line != null ? line.getUnit() : null)
                .roomLabel(line != null ? line.getRoomLabel() : null)
                .floorLabel(line != null ? line.getFloorLabel() : null)
                .plannedQty(planned)
                .approvedQty(approved)
                .remainingQty(remaining)
                .rate(line != null ? line.getRate() : null)
                .amount(line != null ? line.getAmount() : null)
                .build();
    }

    private BigDecimal sumApprovedQty(UUID packageUuid) {
        return claimRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(packageUuid, requireCompany())
                .stream()
                .filter(c -> c.getStatus() == SubcontractorClaimStatus.APPROVED)
                .map(SubcontractorClaim::getClaimedQty)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ScVariationResponse toVariationResponse(ScVariationRequest row) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(row.getPackageUuid(), row.getCompanyId()).orElse(null);
        Project project = resolveProject(row.getProjectId());
        return ScVariationResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .projectId(row.getProjectId())
                .companyId(row.getCompanyId())
                .title(row.getTitle())
                .description(row.getDescription())
                .qtyDelta(row.getQtyDelta())
                .unit(row.getUnit())
                .estimatedCost(row.getEstimatedCost())
                .status(row.getStatus())
                .attachmentPaths(row.getAttachmentPaths())
                .submittedBy(row.getSubmittedBy())
                .submittedAt(row.getSubmittedAt())
                .decidedBy(row.getDecidedBy())
                .decidedAt(row.getDecidedAt())
                .reason(row.getReason())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .packageName(pkg != null ? pkg.getName() : null)
                .projectName(project != null ? project.getName() : null)
                .build();
    }

    private ScSiteReportResponse toSiteReportResponse(ScSiteReport row) {
        SubcontractorPackage pkg = row.getPackageUuid() != null
                ? packageRepository.findByUuidAndCompanyId(row.getPackageUuid(), row.getCompanyId()).orElse(null) : null;
        Project project = resolveProject(row.getProjectId());
        return ScSiteReportResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .projectId(row.getProjectId())
                .companyId(row.getCompanyId())
                .reportType(row.getReportType())
                .title(row.getTitle())
                .description(row.getDescription())
                .delayReasonCode(row.getDelayReasonCode())
                .expectedDate(row.getExpectedDate())
                .deliveredDate(row.getDeliveredDate())
                .materialName(row.getMaterialName())
                .quantity(row.getQuantity())
                .unit(row.getUnit())
                .status(row.getStatus())
                .attachmentPaths(row.getAttachmentPaths())
                .raisedBy(row.getRaisedBy())
                .acknowledgedAt(row.getAcknowledgedAt())
                .resolvedAt(row.getResolvedAt())
                .resolutionNotes(row.getResolutionNotes())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .packageName(pkg != null ? pkg.getName() : null)
                .projectName(project != null ? project.getName() : null)
                .build();
    }

    private ScInvoiceResponse toInvoiceResponse(ScInvoice row) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(row.getPackageUuid(), row.getCompanyId()).orElse(null);
        Project project = resolveProject(row.getProjectId());
        BigDecimal total = (row.getAmount() != null ? row.getAmount() : BigDecimal.ZERO)
                .add(row.getTaxAmount() != null ? row.getTaxAmount() : BigDecimal.ZERO);
        return ScInvoiceResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .projectId(row.getProjectId())
                .companyId(row.getCompanyId())
                .claimUuid(row.getClaimUuid())
                .invoiceNumber(row.getInvoiceNumber())
                .amount(row.getAmount())
                .taxAmount(row.getTaxAmount())
                .currency(row.getCurrency())
                .notes(row.getNotes())
                .status(row.getStatus())
                .attachmentPaths(row.getAttachmentPaths())
                .submittedBy(row.getSubmittedBy())
                .submittedAt(row.getSubmittedAt())
                .decidedBy(row.getDecidedBy())
                .decidedAt(row.getDecidedAt())
                .paidAt(row.getPaidAt())
                .paymentReference(row.getPaymentReference())
                .reason(row.getReason())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .packageName(pkg != null ? pkg.getName() : null)
                .projectName(project != null ? project.getName() : null)
                .totalAmount(total)
                .build();
    }

    private ScSnagPortalResponse toSnagResponse(Snag snag) {
        Project project = resolveProject(snag.getProjectId());
        return ScSnagPortalResponse.builder()
                .uuid(snag.getUuid())
                .projectId(snag.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .title(snag.getTitle())
                .description(snag.getDescription())
                .location(snag.getLocation())
                .status(snag.getStatus())
                .severity(snag.getSeverity())
                .dueDate(snag.getDueDate())
                .createdAt(snag.getCreatedAt())
                .build();
    }

    private SubcontractorPackage requireAppointedPackage(AuthPrincipal principal, UUID packageUuid) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        if (pkg.getAppointedAccountId() == null || !pkg.getAppointedAccountId().equals(principal.getAccountId())) {
            throw new ForbiddenException("Not appointed to this package");
        }
        if (pkg.getStatus() == SubcontractorPackageStatus.OPEN) {
            throw new ForbiddenException("Package not appointed to you");
        }
        return pkg;
    }

    private void assertProjectAppointed(AuthPrincipal principal, Long projectId) {
        boolean appointed = myAppointedPackages().stream()
                .anyMatch(p -> Objects.equals(p.getProjectId(), projectId));
        if (!appointed && !isStaff(principal)) {
            throw new ForbiddenException("Not appointed on this project");
        }
    }

    private ScVariationRequest requireVariation(UUID uuid) {
        return variationRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Variation not found"));
    }

    private ScVariationRequest requireSubmittedVariation(UUID uuid, Long projectId) {
        ScVariationRequest row = requireVariation(uuid);
        if (!row.getProjectId().equals(projectId)) {
            throw new BadRequestException("Variation does not belong to this project");
        }
        if (row.getStatus() != ScVariationStatus.SUBMITTED) {
            throw new BadRequestException("Variation is not pending approval");
        }
        return row;
    }

    private ScSiteReport requireSiteReport(UUID uuid, Long projectId) {
        ScSiteReport row = siteReportRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Site report not found"));
        if (!row.getProjectId().equals(projectId)) {
            throw new BadRequestException("Report does not belong to this project");
        }
        return row;
    }

    private ScInvoice requireInvoice(UUID uuid) {
        return invoiceRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private ScInvoice requireSubmittedInvoice(UUID uuid, Long projectId) {
        ScInvoice row = requireInvoice(uuid);
        if (!row.getProjectId().equals(projectId)) {
            throw new BadRequestException("Invoice does not belong to this project");
        }
        if (row.getStatus() != ScInvoiceStatus.SUBMITTED) {
            throw new BadRequestException("Invoice is not pending approval");
        }
        return row;
    }

    private String storeFile(MultipartFile file, ScVariationRequest row) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }
        return fileStorageService.store(file, row.getCompanyId(), row.getProjectId(), "sc-variations");
    }

    private String storeFile(MultipartFile file, ScInvoice row) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }
        return fileStorageService.store(file, row.getCompanyId(), row.getProjectId(), "sc-invoices");
    }

    private Project resolveProject(Long projectId) {
        if (projectId == null) return null;
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

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private String appendPath(String existing, String path) {
        if (!StringUtils.hasText(path)) return existing;
        if (!StringUtils.hasText(existing)) return path.trim();
        return existing + "," + path.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) throw new ForbiddenException("Company context required");
        return companyId;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireScPortalUser() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireActivePortalUser(principal);
        return principal;
    }

    private void assertBankReadyForPayment(AuthPrincipal principal) {
        ScCompanyProfile profile = profileRepository
                .findByAdminAccountIdAndCompanyId(principal.getAccountId(), requireCompany())
                .orElse(null);
        if (profile == null || profile.getOrganizationUuid() == null) {
            throw new BadRequestException("Company profile and bank details are required before invoicing.");
        }
        bankDetailRepository.findByOrganizationUuid(profile.getOrganizationUuid()).ifPresentOrElse(bank -> {
            if (bank.getIban() == null || bank.getIban().isBlank()) {
                throw new BadRequestException("Bank IBAN must be recorded before submitting invoices.");
            }
            if (bank.getBankLetterFilePath() == null || bank.getBankLetterFilePath().isBlank()) {
                throw new BadRequestException("Bank confirmation letter must be uploaded before submitting invoices.");
            }
            if (bank.getVerificationStatus() != ScBankVerificationStatus.VERIFIED
                    && bank.getVerificationStatus() != ScBankVerificationStatus.PENDING
                    && bank.getVerificationStatus() != ScBankVerificationStatus.CHANGE_PENDING) {
                throw new BadRequestException("Bank details must be verified before submitting invoices.");
            }
        }, () -> {
            throw new BadRequestException("Bank details must be recorded before submitting invoices.");
        });
    }

    private AuthPrincipal requireAuthenticatedLegacy() {
        return requireAuthenticated();
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (!isStaff(principal)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requirePmOrAdmin() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("PM/Admin access required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER);
        if (!allowed) {
            throw new ForbiddenException("PM/Admin access required");
        }
        return principal;
    }

    private boolean isStaff(AuthPrincipal principal) {
        return principal.getRoles() != null
                && principal.getRoles().stream().anyMatch(r -> r != Role.CLIENT && r != Role.SUBCONTRACTOR);
    }
}
