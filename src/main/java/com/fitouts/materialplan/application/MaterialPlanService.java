package com.fitouts.materialplan.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.materialplan.api.MaterialPlanLineRequest;
import com.fitouts.materialplan.api.MaterialPlanLineResponse;
import com.fitouts.materialplan.api.MaterialPlanResponse;
import com.fitouts.materialplan.api.MaterialPlanUpdateRequest;
import com.fitouts.materialplan.domain.MaterialPlanStatus;
import com.fitouts.materialplan.domain.ProjectMaterialPlan;
import com.fitouts.materialplan.domain.ProjectMaterialPlanLine;
import com.fitouts.materialplan.domain.ProjectMaterialPlanLineRepository;
import com.fitouts.materialplan.domain.ProjectMaterialPlanRepository;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.procurement.application.StockService;
import com.fitouts.procurement.domain.Material;
import com.fitouts.procurement.domain.MaterialStock;
import com.fitouts.procurement.domain.MaterialStockRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterial;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialPlanService {

    private final ProjectMaterialPlanRepository planRepository;
    private final ProjectMaterialPlanLineRepository lineRepository;
    private final ProjectService projectService;
    private final PlanningService planningService;
    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final WorkItemMaterialRepository workItemMaterialRepository;
    private final MaterialStockRepository materialStockRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public MaterialPlanResponse get(Long projectId) {
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return planRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(project.getId(), companyId));
    }

    @Transactional
    public MaterialPlanResponse generate(Long projectId) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        ProjectMaterialPlan plan = planRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseGet(() -> {
                    ProjectMaterialPlan created = new ProjectMaterialPlan();
                    created.setProjectId(project.getId());
                    created.setCompanyId(companyId);
                    created.setStatus(MaterialPlanStatus.DRAFT);
                    return planRepository.save(created);
                });

        lineRepository.deleteByPlanUuid(plan.getUuid());

        BoqDocument approvedBoq = findLatestApprovedBoq(project.getId(), companyId);
        List<ProjectMaterialPlanLine> lines = new ArrayList<>();
        if (approvedBoq != null) {
            lines = buildLinesFromBoq(plan.getUuid(), approvedBoq, companyId);
            plan.setGeneratedFromBoqId(approvedBoq.getId());
        } else {
            plan.setGeneratedFromBoqId(null);
        }

        plan.setStatus(MaterialPlanStatus.DRAFT);
        plan.setUpdatedBy(principal.getAccountId());
        plan = planRepository.save(plan);
        if (!lines.isEmpty()) {
            lineRepository.saveAll(lines);
        }

        syncPlanningMaterialStatus(project.getId(), plan.getStatus(), lines.size(), principal.getAccountId());
        return toResponse(plan);
    }

    @Transactional
    public MaterialPlanResponse update(Long projectId, MaterialPlanUpdateRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        ProjectMaterialPlan plan = planRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseGet(() -> {
                    ProjectMaterialPlan created = new ProjectMaterialPlan();
                    created.setProjectId(project.getId());
                    created.setCompanyId(companyId);
                    created.setStatus(MaterialPlanStatus.DRAFT);
                    return planRepository.save(created);
                });

        if (request.getStatus() != null) {
            plan.setStatus(request.getStatus());
        }

        if (request.getLines() != null) {
            lineRepository.deleteByPlanUuid(plan.getUuid());
            List<ProjectMaterialPlanLine> lines = new ArrayList<>();
            int order = 0;
            for (MaterialPlanLineRequest lr : request.getLines()) {
                lines.add(toLineEntity(plan.getUuid(), lr, order++));
            }
            if (!lines.isEmpty()) {
                lineRepository.saveAll(lines);
            }
        }

        plan.setUpdatedBy(principal.getAccountId());
        plan = planRepository.save(plan);

        int lineCount = lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid()).size();
        syncPlanningMaterialStatus(project.getId(), plan.getStatus(), lineCount, principal.getAccountId());
        return toResponse(plan);
    }

    @Transactional
    public MaterialPlanResponse reserve(Long projectId) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        ProjectMaterialPlan plan = planRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseThrow(() -> new BadRequestException("Material plan not found — generate or create lines first"));

        List<ProjectMaterialPlanLine> lines = lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid());
        for (ProjectMaterialPlanLine line : lines) {
            if (line.isShortageFlag()) {
                continue;
            }
            BigDecimal planned = line.getPlannedQty() != null ? line.getPlannedQty() : BigDecimal.ZERO;
            BigDecimal alreadyReserved = line.getReservedQty() != null ? line.getReservedQty() : BigDecimal.ZERO;
            BigDecimal delta = planned.subtract(alreadyReserved);
            if (delta.compareTo(BigDecimal.ZERO) > 0 && line.getMaterialId() != null) {
                stockService.increaseReserved(line.getMaterialId(), delta);
            }
            line.setReservedQty(planned);
        }
        lineRepository.saveAll(lines);
        plan.setUpdatedBy(principal.getAccountId());
        planRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        StringBuilder sb = new StringBuilder();
        sb.append("materialName,plannedQty,stockQtySnapshot,unit,shortageFlag,reservedQty,substituteReason,notes,sortOrder\n");
        planRepository.findByProjectIdAndCompanyId(project.getId(), companyId).ifPresent(plan -> {
            for (ProjectMaterialPlanLine line : lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid())) {
                sb.append(csv(line.getMaterialName())).append(',')
                        .append(line.getPlannedQty() != null ? line.getPlannedQty() : "").append(',')
                        .append(line.getStockQtySnapshot() != null ? line.getStockQtySnapshot() : "").append(',')
                        .append(csv(line.getUnit())).append(',')
                        .append(line.isShortageFlag()).append(',')
                        .append(line.getReservedQty() != null ? line.getReservedQty() : "").append(',')
                        .append(csv(line.getSubstituteReason())).append(',')
                        .append(csv(line.getNotes())).append(',')
                        .append(line.getSortOrder())
                        .append('\n');
            }
        });
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private List<ProjectMaterialPlanLine> buildLinesFromBoq(UUID planUuid, BoqDocument boq, UUID companyId) {
        List<BoqLine> boqLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(boq.getId());
        List<UUID> workItemIds = boqLines.stream()
                .filter(l -> l.getQtoLine() != null && l.getQtoLine().getWorkItem() != null)
                .map(l -> l.getQtoLine().getWorkItem().getId())
                .distinct()
                .toList();

        if (workItemIds.isEmpty()) {
            return List.of();
        }

        List<WorkItemMaterial> workItemMaterials = workItemMaterialRepository.findByWorkItemIdIn(workItemIds);
        Map<UUID, List<WorkItemMaterial>> byWorkItem = workItemMaterials.stream()
                .collect(Collectors.groupingBy(wim -> wim.getWorkItem().getId()));

        Map<UUID, AggregatedMaterial> aggregated = new HashMap<>();
        for (BoqLine boqLine : boqLines) {
            if (boqLine.getQtoLine() == null || boqLine.getQtoLine().getWorkItem() == null) {
                continue;
            }
            UUID workItemId = boqLine.getQtoLine().getWorkItem().getId();
            List<WorkItemMaterial> mats = byWorkItem.getOrDefault(workItemId, List.of());
            BigDecimal boqQty = boqLine.getQuantity() != null ? boqLine.getQuantity() : BigDecimal.ZERO;
            for (WorkItemMaterial wim : mats) {
                Material material = wim.getMaterial();
                if (material == null || Boolean.TRUE.equals(material.getDeleted())) {
                    continue;
                }
                BigDecimal perUnit = wim.getQuantityPerUnit() != null ? wim.getQuantityPerUnit() : BigDecimal.ONE;
                BigDecimal wastage = wim.getWastagePercent() != null ? wim.getWastagePercent() : BigDecimal.ZERO;
                BigDecimal factor = BigDecimal.ONE.add(wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                BigDecimal planned = boqQty.multiply(perUnit).multiply(factor).setScale(4, RoundingMode.HALF_UP);

                AggregatedMaterial agg = aggregated.computeIfAbsent(material.getId(), id -> {
                    AggregatedMaterial a = new AggregatedMaterial();
                    a.materialId = material.getId();
                    a.materialName = material.getMaterialName();
                    a.unit = material.getUnitType() != null ? material.getUnitType().name() : null;
                    a.plannedQty = BigDecimal.ZERO;
                    return a;
                });
                agg.plannedQty = agg.plannedQty.add(planned);
            }
        }

        if (aggregated.isEmpty()) {
            return List.of();
        }

        Map<UUID, BigDecimal> stockByMaterial = materialStockRepository
                .findByCompanyUuidAndMaterialIdIn(companyId, aggregated.keySet())
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getMaterial().getId(),
                        MaterialStock::getQuantityOnHand,
                        (a, b) -> a));

        List<ProjectMaterialPlanLine> lines = new ArrayList<>();
        int order = 0;
        for (AggregatedMaterial agg : aggregated.values()) {
            BigDecimal stock = stockByMaterial.getOrDefault(agg.materialId, BigDecimal.ZERO);
            ProjectMaterialPlanLine line = new ProjectMaterialPlanLine();
            line.setPlanUuid(planUuid);
            line.setMaterialId(agg.materialId);
            line.setMaterialName(agg.materialName);
            line.setPlannedQty(agg.plannedQty);
            line.setStockQtySnapshot(stock);
            line.setUnit(agg.unit);
            line.setShortageFlag(agg.plannedQty.compareTo(stock) > 0);
            line.setReservedQty(BigDecimal.ZERO);
            line.setSortOrder(order++);
            lines.add(line);
        }
        return lines;
    }

    private BoqDocument findLatestApprovedBoq(Long projectId, UUID companyId) {
        return boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(d -> companyId.equals(d.getCompanyId()))
                .filter(d -> d.getStatus() == BoqDocumentStatus.APPROVED || d.getStatus() == BoqDocumentStatus.FINAL)
                .findFirst()
                .orElse(null);
    }

    private ProjectMaterialPlanLine toLineEntity(UUID planUuid, MaterialPlanLineRequest lr, int defaultOrder) {
        if (!StringUtils.hasText(lr.getMaterialName())) {
            throw new BadRequestException("materialName is required for each line");
        }
        BigDecimal planned = lr.getPlannedQty() != null ? lr.getPlannedQty() : BigDecimal.ZERO;
        BigDecimal stock = lr.getStockQtySnapshot() != null ? lr.getStockQtySnapshot() : BigDecimal.ZERO;
        boolean shortage = lr.getShortageFlag() != null
                ? lr.getShortageFlag()
                : planned.compareTo(stock) > 0;

        ProjectMaterialPlanLine line = new ProjectMaterialPlanLine();
        line.setPlanUuid(planUuid);
        line.setMaterialId(lr.getMaterialId());
        line.setMaterialName(lr.getMaterialName().trim());
        line.setPlannedQty(planned);
        line.setStockQtySnapshot(stock);
        line.setUnit(lr.getUnit());
        line.setShortageFlag(shortage);
        line.setReservedQty(lr.getReservedQty() != null ? lr.getReservedQty() : BigDecimal.ZERO);
        line.setNotes(lr.getNotes());
        line.setSubstituteReason(trimToNull(lr.getSubstituteReason()));
        line.setSortOrder(lr.getSortOrder() != null ? lr.getSortOrder() : defaultOrder);
        return line;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void syncPlanningMaterialStatus(Long projectId, MaterialPlanStatus planStatus, int lineCount, Long updatedBy) {
        PlanAreaStatus area;
        if (planStatus == MaterialPlanStatus.READY) {
            area = PlanAreaStatus.READY;
        } else if (lineCount > 0) {
            area = PlanAreaStatus.IN_PROGRESS;
        } else {
            area = PlanAreaStatus.NOT_STARTED;
        }
        planningService.syncMaterialStatus(projectId, area, updatedBy);
    }

    private MaterialPlanResponse toResponse(ProjectMaterialPlan plan) {
        List<MaterialPlanLineResponse> lines = lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid())
                .stream()
                .map(this::toLineResponse)
                .toList();
        return MaterialPlanResponse.builder()
                .uuid(plan.getUuid())
                .projectId(plan.getProjectId())
                .companyId(plan.getCompanyId())
                .status(plan.getStatus())
                .generatedFromBoqId(plan.getGeneratedFromBoqId())
                .updatedBy(plan.getUpdatedBy())
                .updatedAt(plan.getUpdatedAt())
                .lines(lines)
                .build();
    }

    private MaterialPlanLineResponse toLineResponse(ProjectMaterialPlanLine line) {
        return MaterialPlanLineResponse.builder()
                .uuid(line.getUuid())
                .materialId(line.getMaterialId())
                .materialName(line.getMaterialName())
                .plannedQty(line.getPlannedQty())
                .stockQtySnapshot(line.getStockQtySnapshot())
                .unit(line.getUnit())
                .shortageFlag(line.isShortageFlag())
                .reservedQty(line.getReservedQty())
                .notes(line.getNotes())
                .substituteReason(line.getSubstituteReason())
                .sortOrder(line.getSortOrder())
                .build();
    }

    private MaterialPlanResponse emptyResponse(Long projectId, UUID companyId) {
        return MaterialPlanResponse.builder()
                .uuid(null)
                .projectId(projectId)
                .companyId(companyId)
                .status(MaterialPlanStatus.DRAFT)
                .generatedFromBoqId(null)
                .updatedBy(null)
                .updatedAt(null)
                .lines(List.of())
                .build();
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private static class AggregatedMaterial {
        UUID materialId;
        String materialName;
        String unit;
        BigDecimal plannedQty;
    }
}
