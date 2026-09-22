package com.fitouts.profitloss.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.procurement.domain.StockMovementRepository;
import com.fitouts.profitloss.api.CompanyPnlResponse;
import com.fitouts.profitloss.api.OverheadRuleResponse;
import com.fitouts.profitloss.api.OverheadRuleUpsertRequest;
import com.fitouts.profitloss.api.ProjectPnlResponse;
import com.fitouts.profitloss.domain.ProjectPnlSnapshot;
import com.fitouts.profitloss.domain.ProjectPnlSnapshotRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.StockMovementType;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.domain.ScPaymentCertificate;
import com.fitouts.subcontractor.domain.ScPaymentCertificateRepository;
import com.fitouts.subcontractor.domain.ScPaymentCertificateStatus;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PnlCalculationService {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Set<Role> PNL_ROLES = EnumSet.of(
            Role.FINANCE, Role.BUSINESS_OWNER, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<ScPaymentCertificateStatus> SC_COST_STATUSES = EnumSet.of(
            ScPaymentCertificateStatus.ISSUED, ScPaymentCertificateStatus.PAID);

    private final ProjectPnlSnapshotRepository snapshotRepository;
    private final ProjectCommercialRepository commercialRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ScPaymentCertificateRepository certificateRepository;
    private final ProjectRepository projectRepository;
    private final OverheadAllocationService overheadAllocationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProjectPnlSnapshot recalculate(Long projectId, UUID companyId) {
        if (projectId == null || companyId == null) {
            throw new BadRequestException("projectId and companyId are required");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project does not belong to company");
        }

        ProjectCommercial commercial = commercialRepository
                .findByProjectIdAndCompanyId(projectId, companyId)
                .orElse(null);

        BigDecimal contractValue = commercial != null && commercial.getCurrentContractValue() != null
                ? commercial.getCurrentContractValue()
                : BigDecimal.ZERO;
        BigDecimal variationCost = commercial != null && commercial.getCurrentCost() != null
                ? commercial.getCurrentCost()
                : BigDecimal.ZERO;
        BigDecimal originalContract = commercial != null ? commercial.getOriginalContractValue() : null;
        BigDecimal originalCost = commercial != null ? commercial.getOriginalCost() : null;

        BigDecimal materialCost = nullSafe(stockMovementRepository.sumTotalCostByProjectAndType(
                companyId, projectId, StockMovementType.ISSUE));

        BigDecimal scCertifiedCost = certificateRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)
                .stream()
                .filter(c -> c.getStatus() != null && SC_COST_STATUSES.contains(c.getStatus()))
                .map(ScPaymentCertificate::getCertifiedValue)
                .map(PnlCalculationService::nullSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal labourCost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal overhead = overheadAllocationService.allocate(companyId, contractValue);

        BigDecimal totalCost = materialCost
                .add(labourCost)
                .add(scCertifiedCost)
                .add(variationCost)
                .add(overhead)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal margin = contractValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);

        BigDecimal marginVsOriginal = null;
        if (originalContract != null && originalCost != null) {
            BigDecimal originalMargin = originalContract.subtract(originalCost);
            marginVsOriginal = margin.subtract(originalMargin).setScale(2, RoundingMode.HALF_UP);
        }

        String period = currentPeriod();
        ProjectPnlSnapshot snapshot = snapshotRepository
                .findByCompanyIdAndProjectIdAndPeriodYearMonth(companyId, projectId, period)
                .orElseGet(ProjectPnlSnapshot::new);
        snapshot.setCompanyId(companyId);
        snapshot.setProjectId(projectId);
        snapshot.setPeriodYearMonth(period);
        snapshot.setContractValue(contractValue);
        snapshot.setMaterialCost(materialCost);
        snapshot.setLabourCost(labourCost);
        snapshot.setScCertifiedCost(scCertifiedCost);
        snapshot.setVariationCost(variationCost);
        snapshot.setOverheadAllocated(overhead);
        snapshot.setTotalCost(totalCost);
        snapshot.setMargin(margin);
        snapshot.setOriginalContractValue(originalContract);
        snapshot.setOriginalEstimatedCost(originalCost);
        snapshot.setMarginVsOriginalEstimate(marginVsOriginal);
        snapshot.setCalculatedAt(OffsetDateTime.now());
        return snapshotRepository.save(snapshot);
    }

    /** Fire-and-forget style: never throws to callers. */
    public void recalculateSafe(Long projectId, UUID companyId) {
        try {
            recalculate(projectId, companyId);
        } catch (Exception e) {
            log.warn("P&L recalculation failed for project {} company {}: {}",
                    projectId, companyId, e.getMessage());
        }
    }

    @Transactional
    public void recalculateAllForCompany(UUID companyId) {
        if (companyId == null) {
            return;
        }
        for (Project project : projectRepository.findByCompanyIdAndIsDeletedFalse(companyId)) {
            recalculateSafe(project.getId(), companyId);
        }
    }

    @Transactional
    public ProjectPnlResponse getProjectPnl(Long projectId) {
        requirePnlReader();
        UUID companyId = requireCompany();
        Project project = requireProject(projectId, companyId);
        ProjectPnlSnapshot snapshot = snapshotRepository
                .findFirstByCompanyIdAndProjectIdOrderByCalculatedAtDesc(companyId, projectId)
                .orElseGet(() -> recalculate(projectId, companyId));
        return toProjectResponse(snapshot, project.getName());
    }

    @Transactional
    public CompanyPnlResponse getCompanyPnl(String yearMonth) {
        requirePnlReader();
        UUID companyId = requireCompany();
        String period = normalizePeriod(yearMonth);
        List<Project> projects = projectRepository.findByCompanyIdAndIsDeletedFalse(companyId);
        for (Project project : projects) {
            if (snapshotRepository
                    .findByCompanyIdAndProjectIdAndPeriodYearMonth(companyId, project.getId(), period)
                    .isEmpty()
                    || period.equals(currentPeriod())) {
                recalculateSafe(project.getId(), companyId);
            }
        }
        List<ProjectPnlSnapshot> snapshots = snapshotRepository
                .findByCompanyIdAndPeriodYearMonthOrderByProjectIdAsc(companyId, period);
        return aggregate(period, snapshots, projects);
    }

    @Transactional(readOnly = true)
    public OverheadRuleResponse getOverheadRule() {
        requirePnlReader();
        return overheadAllocationService.getRule();
    }

    @Transactional
    public OverheadRuleResponse upsertOverheadRule(OverheadRuleUpsertRequest request) {
        requirePnlReader();
        OverheadRuleResponse saved = overheadAllocationService.upsertRule(request);
        UUID companyId = requireCompany();
        recalculateAllForCompany(companyId);
        return saved;
    }

    public ProjectPnlResponse toProjectResponse(ProjectPnlSnapshot s, String projectName) {
        return ProjectPnlResponse.builder()
                .uuid(s.getUuid())
                .projectId(s.getProjectId())
                .projectName(projectName)
                .periodYearMonth(s.getPeriodYearMonth())
                .contractValue(s.getContractValue())
                .materialCost(s.getMaterialCost())
                .labourCost(s.getLabourCost())
                .scCertifiedCost(s.getScCertifiedCost())
                .variationCost(s.getVariationCost())
                .overheadAllocated(s.getOverheadAllocated())
                .totalCost(s.getTotalCost())
                .margin(s.getMargin())
                .marginPercent(marginPercent(s.getContractValue(), s.getMargin()))
                .originalContractValue(s.getOriginalContractValue())
                .originalEstimatedCost(s.getOriginalEstimatedCost())
                .marginVsOriginalEstimate(s.getMarginVsOriginalEstimate())
                .calculatedAt(s.getCalculatedAt())
                .build();
    }

    private CompanyPnlResponse aggregate(String period, List<ProjectPnlSnapshot> snapshots, List<Project> projects) {
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (Project p : projects) {
            names.put(p.getId(), p.getName());
        }
        List<ProjectPnlResponse> rows = snapshots.stream()
                .map(s -> toProjectResponse(s, names.getOrDefault(s.getProjectId(), "Project " + s.getProjectId())))
                .toList();

        BigDecimal contract = sum(rows, ProjectPnlResponse::getContractValue);
        BigDecimal material = sum(rows, ProjectPnlResponse::getMaterialCost);
        BigDecimal labour = sum(rows, ProjectPnlResponse::getLabourCost);
        BigDecimal sc = sum(rows, ProjectPnlResponse::getScCertifiedCost);
        BigDecimal variation = sum(rows, ProjectPnlResponse::getVariationCost);
        BigDecimal overhead = sum(rows, ProjectPnlResponse::getOverheadAllocated);
        BigDecimal totalCost = sum(rows, ProjectPnlResponse::getTotalCost);
        BigDecimal margin = sum(rows, ProjectPnlResponse::getMargin);

        return CompanyPnlResponse.builder()
                .periodYearMonth(period)
                .contractValue(contract)
                .materialCost(material)
                .labourCost(labour)
                .scCertifiedCost(sc)
                .variationCost(variation)
                .overheadAllocated(overhead)
                .totalCost(totalCost)
                .margin(margin)
                .marginPercent(marginPercent(contract, margin))
                .projects(rows)
                .build();
    }

    private static BigDecimal sum(List<ProjectPnlResponse> rows,
                                  java.util.function.Function<ProjectPnlResponse, BigDecimal> getter) {
        return rows.stream()
                .map(getter)
                .map(PnlCalculationService::nullSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal marginPercent(BigDecimal contract, BigDecimal margin) {
        if (contract == null || contract.signum() == 0 || margin == null) {
            return null;
        }
        return margin.multiply(BigDecimal.valueOf(100))
                .divide(contract, 2, RoundingMode.HALF_UP);
    }

    public static String currentPeriod() {
        return OffsetDateTime.now(DUBAI).format(YEAR_MONTH);
    }

    public static String normalizePeriod(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            return currentPeriod();
        }
        String trimmed = yearMonth.trim();
        if (!trimmed.matches("\\d{4}-\\d{2}")) {
            throw new BadRequestException("yearMonth must be YYYY-MM");
        }
        return trimmed;
    }

    private Project requireProject(Long projectId, UUID companyId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!companyId.equals(project.getCompanyId())) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    private AuthPrincipal requirePnlReader() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        if (principal.getRoles() == null || principal.getRoles().stream().noneMatch(PNL_ROLES::contains)) {
            throw new ForbiddenException("Finance or Director role required");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context required");
        }
        return companyId;
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
