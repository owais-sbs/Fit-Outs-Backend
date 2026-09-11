package com.fitouts.schedule.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.materialplan.domain.ProjectMaterialPlanLine;
import com.fitouts.materialplan.domain.ProjectMaterialPlanLineRepository;
import com.fitouts.materialplan.domain.ProjectMaterialPlanRepository;
import com.fitouts.procurement.api.StockIssueRequest;
import com.fitouts.procurement.api.StockMovementResponse;
import com.fitouts.procurement.application.StockService;
import com.fitouts.procurement.domain.Material;
import com.fitouts.procurement.domain.MaterialRepository;
import com.fitouts.schedule.api.ActivityMaterialSummaryResponse;
import com.fitouts.schedule.api.MaterialIssueRequest;
import com.fitouts.schedule.api.MaterialIssueResponse;
import com.fitouts.schedule.domain.ActivityMaterialIssue;
import com.fitouts.schedule.domain.ActivityMaterialIssueRepository;
import com.fitouts.schedule.domain.ActivityMaterialIssueStatus;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityMaterialIssueService {

    private final ActivityMaterialIssueRepository issueRepository;
    private final ProjectMaterialPlanRepository planRepository;
    private final ProjectMaterialPlanLineRepository planLineRepository;
    private final MaterialRepository materialRepository;
    private final StockService stockService;

    @Transactional
    public List<MaterialIssueResponse> declareIssues(
            ScheduleActivity activity,
            UUID progressUpdateUuid,
            List<MaterialIssueRequest> requests,
            Long createdBy) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        UUID companyId = activity.getCompanyId();
        Map<UUID, ProjectMaterialPlanLine> planByMaterial = loadPlanLinesByMaterial(
                activity.getProjectId(), companyId);
        Map<UUID, ProjectMaterialPlanLine> planByLineUuid = new HashMap<>();
        for (ProjectMaterialPlanLine line : planByMaterial.values()) {
            planByLineUuid.put(line.getUuid(), line);
        }

        List<ActivityMaterialIssue> saved = new ArrayList<>();
        for (MaterialIssueRequest req : requests) {
            if (req == null) {
                continue;
            }
            if (req.getMaterialId() == null) {
                throw new BadRequestException("materialId is required for each material issue");
            }
            if (req.getQty() == null || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("material issue qty must be positive");
            }

            Material material = materialRepository.findByIdAndDeletedFalse(req.getMaterialId())
                    .orElseThrow(() -> new NotFoundException("Material not found"));
            if (material.getCompany() == null || !companyId.equals(material.getCompany().getUuid())) {
                throw new BadRequestException("Material does not belong to your company");
            }

            ProjectMaterialPlanLine planLine = null;
            if (req.getPlanLineUuid() != null) {
                planLine = planByLineUuid.get(req.getPlanLineUuid());
                if (planLine == null) {
                    planLine = planLineRepository.findById(req.getPlanLineUuid()).orElse(null);
                }
                if (planLine != null && planLine.getMaterialId() != null
                        && !planLine.getMaterialId().equals(req.getMaterialId())) {
                    throw new BadRequestException("planLineUuid does not match materialId");
                }
            }
            if (planLine == null) {
                planLine = planByMaterial.get(req.getMaterialId());
            }

            ActivityMaterialIssue issue = new ActivityMaterialIssue();
            issue.setActivityUuid(activity.getUuid());
            issue.setProjectId(activity.getProjectId());
            issue.setCompanyId(companyId);
            issue.setProgressUpdateUuid(progressUpdateUuid);
            issue.setPlanLineUuid(planLine != null ? planLine.getUuid() : null);
            issue.setMaterialId(material.getId());
            issue.setMaterialName(StringUtils.hasText(material.getMaterialName())
                    ? material.getMaterialName()
                    : (planLine != null ? planLine.getMaterialName() : null));
            issue.setQty(req.getQty());
            issue.setStatus(ActivityMaterialIssueStatus.DECLARED);
            issue.setCreatedBy(createdBy);
            saved.add(issueRepository.save(issue));
        }

        Map<UUID, BigDecimal> plannedByMaterial = plannedQtyByMaterial(activity.getProjectId(), companyId);
        return saved.stream().map(i -> toResponse(i, plannedByMaterial)).toList();
    }

    @Transactional
    public void postDeclaredToStock(UUID progressUpdateUuid) {
        List<ActivityMaterialIssue> issues = issueRepository
                .findByProgressUpdateUuidOrderByCreatedAtAsc(progressUpdateUuid);
        for (ActivityMaterialIssue issue : issues) {
            if (issue.getStatus() != ActivityMaterialIssueStatus.DECLARED) {
                continue;
            }
            StockIssueRequest stockReq = StockIssueRequest.builder()
                    .materialId(issue.getMaterialId())
                    .quantity(issue.getQty())
                    .projectId(issue.getProjectId())
                    .referenceNo(issue.getUuid().toString())
                    .notes("Activity progress material issue")
                    .build();
            StockMovementResponse movement = stockService.issue(stockReq);
            issue.setStatus(ActivityMaterialIssueStatus.POSTED_TO_STOCK);
            if (movement != null) {
                issue.setStockMovementId(movement.getId());
            }
            issueRepository.save(issue);
        }
    }

    @Transactional
    public void voidDeclared(UUID progressUpdateUuid) {
        List<ActivityMaterialIssue> issues = issueRepository
                .findByProgressUpdateUuidOrderByCreatedAtAsc(progressUpdateUuid);
        for (ActivityMaterialIssue issue : issues) {
            if (issue.getStatus() == ActivityMaterialIssueStatus.DECLARED) {
                issue.setStatus(ActivityMaterialIssueStatus.VOID);
                issueRepository.save(issue);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MaterialIssueResponse> listForProgress(UUID progressUpdateUuid) {
        List<ActivityMaterialIssue> issues = issueRepository
                .findByProgressUpdateUuidOrderByCreatedAtAsc(progressUpdateUuid);
        if (issues.isEmpty()) {
            return List.of();
        }
        Long projectId = issues.get(0).getProjectId();
        UUID companyId = issues.get(0).getCompanyId();
        Map<UUID, BigDecimal> plannedByMaterial = plannedQtyByMaterial(projectId, companyId);
        return issues.stream().map(i -> toResponse(i, plannedByMaterial)).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<MaterialIssueResponse>> listForProgressUpdates(Collection<UUID> progressUpdateUuids) {
        if (progressUpdateUuids == null || progressUpdateUuids.isEmpty()) {
            return Map.of();
        }
        List<ActivityMaterialIssue> issues = issueRepository.findByProgressUpdateUuidIn(progressUpdateUuids);
        Map<UUID, List<MaterialIssueResponse>> byProgress = new HashMap<>();
        Map<String, Map<UUID, BigDecimal>> plannedCache = new HashMap<>();
        for (ActivityMaterialIssue issue : issues) {
            String key = issue.getProjectId() + ":" + issue.getCompanyId();
            Map<UUID, BigDecimal> planned = plannedCache.computeIfAbsent(
                    key, k -> plannedQtyByMaterial(issue.getProjectId(), issue.getCompanyId()));
            byProgress
                    .computeIfAbsent(issue.getProgressUpdateUuid(), u -> new ArrayList<>())
                    .add(toResponse(issue, planned));
        }
        return byProgress;
    }

    @Transactional(readOnly = true)
    public List<ActivityMaterialSummaryResponse> activitySummary(ScheduleActivity activity) {
        UUID companyId = activity.getCompanyId();
        Long projectId = activity.getProjectId();

        Map<UUID, ProjectMaterialPlanLine> planByMaterial = loadPlanLinesByMaterial(projectId, companyId);
        List<ActivityMaterialIssue> issues = issueRepository
                .findByActivityUuidAndCompanyIdOrderByCreatedAtAsc(activity.getUuid(), companyId);

        Map<UUID, SummaryAcc> acc = new LinkedHashMap<>();
        for (ActivityMaterialIssue issue : issues) {
            SummaryAcc row = acc.computeIfAbsent(issue.getMaterialId(), id -> {
                ProjectMaterialPlanLine plan = planByMaterial.get(id);
                SummaryAcc s = new SummaryAcc();
                s.materialId = id;
                s.materialName = issue.getMaterialName();
                if (plan != null) {
                    s.planLineUuid = plan.getUuid();
                    s.plannedQty = nz(plan.getPlannedQty());
                    s.unit = plan.getUnit();
                    if (!StringUtils.hasText(s.materialName)) {
                        s.materialName = plan.getMaterialName();
                    }
                } else {
                    s.planLineUuid = issue.getPlanLineUuid();
                    s.plannedQty = BigDecimal.ZERO;
                }
                return s;
            });
            if (!StringUtils.hasText(row.materialName) && StringUtils.hasText(issue.getMaterialName())) {
                row.materialName = issue.getMaterialName();
            }
            if (issue.getStatus() == ActivityMaterialIssueStatus.POSTED_TO_STOCK) {
                row.issuedQty = row.issuedQty.add(nz(issue.getQty()));
            } else if (issue.getStatus() == ActivityMaterialIssueStatus.DECLARED) {
                row.declaredQty = row.declaredQty.add(nz(issue.getQty()));
            }
        }

        List<ActivityMaterialSummaryResponse> rows = new ArrayList<>();
        for (SummaryAcc s : acc.values()) {
            if (s.issuedQty.compareTo(BigDecimal.ZERO) == 0 && s.declaredQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            rows.add(ActivityMaterialSummaryResponse.builder()
                    .materialId(s.materialId)
                    .materialName(s.materialName)
                    .planLineUuid(s.planLineUuid)
                    .plannedQty(s.plannedQty)
                    .issuedQty(s.issuedQty)
                    .declaredQty(s.declaredQty)
                    .remainingQty(s.plannedQty.subtract(s.issuedQty))
                    .unit(s.unit)
                    .build());
        }
        return rows;
    }

    private static final class SummaryAcc {
        UUID materialId;
        String materialName;
        UUID planLineUuid;
        BigDecimal plannedQty = BigDecimal.ZERO;
        BigDecimal issuedQty = BigDecimal.ZERO;
        BigDecimal declaredQty = BigDecimal.ZERO;
        String unit;
    }

    @Transactional(readOnly = true)
    public List<MaterialVarianceRow> projectVariance(Long projectId, UUID companyId) {
        Map<UUID, ProjectMaterialPlanLine> planByMaterial = loadPlanLinesByMaterial(projectId, companyId);
        List<ActivityMaterialIssue> posted = issueRepository.findByProjectIdAndCompanyIdAndStatus(
                projectId, companyId, ActivityMaterialIssueStatus.POSTED_TO_STOCK);

        Map<UUID, BigDecimal> issuedByMaterial = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (ProjectMaterialPlanLine line : planByMaterial.values()) {
            if (line.getMaterialId() != null) {
                names.put(line.getMaterialId(), line.getMaterialName());
            }
        }
        for (ActivityMaterialIssue issue : posted) {
            issuedByMaterial.merge(issue.getMaterialId(), nz(issue.getQty()), BigDecimal::add);
            if (StringUtils.hasText(issue.getMaterialName())) {
                names.putIfAbsent(issue.getMaterialId(), issue.getMaterialName());
            }
        }

        Map<UUID, MaterialVarianceRow> rows = new LinkedHashMap<>();
        for (ProjectMaterialPlanLine line : planByMaterial.values()) {
            if (line.getMaterialId() == null) {
                continue;
            }
            BigDecimal issued = issuedByMaterial.getOrDefault(line.getMaterialId(), BigDecimal.ZERO);
            rows.put(line.getMaterialId(), new MaterialVarianceRow(
                    line.getMaterialId(),
                    line.getMaterialName(),
                    nz(line.getPlannedQty()),
                    issued,
                    nz(line.getPlannedQty()).subtract(issued),
                    line.getUnit()));
        }
        for (Map.Entry<UUID, BigDecimal> e : issuedByMaterial.entrySet()) {
            if (rows.containsKey(e.getKey())) {
                continue;
            }
            rows.put(e.getKey(), new MaterialVarianceRow(
                    e.getKey(),
                    names.get(e.getKey()),
                    BigDecimal.ZERO,
                    e.getValue(),
                    e.getValue().negate(),
                    null));
        }
        return new ArrayList<>(rows.values());
    }

    public record MaterialVarianceRow(
            UUID materialId,
            String materialName,
            BigDecimal plannedQty,
            BigDecimal issuedQty,
            BigDecimal remainingQty,
            String unit) {
    }

    private Map<UUID, ProjectMaterialPlanLine> loadPlanLinesByMaterial(Long projectId, UUID companyId) {
        Map<UUID, ProjectMaterialPlanLine> byMaterial = new LinkedHashMap<>();
        planRepository.findByProjectIdAndCompanyId(projectId, companyId).ifPresent(plan -> {
            for (ProjectMaterialPlanLine line : planLineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid())) {
                if (line.getMaterialId() != null) {
                    byMaterial.putIfAbsent(line.getMaterialId(), line);
                }
            }
        });
        return byMaterial;
    }

    private Map<UUID, BigDecimal> plannedQtyByMaterial(Long projectId, UUID companyId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Map.Entry<UUID, ProjectMaterialPlanLine> e : loadPlanLinesByMaterial(projectId, companyId).entrySet()) {
            map.put(e.getKey(), nz(e.getValue().getPlannedQty()));
        }
        return map;
    }

    private MaterialIssueResponse toResponse(ActivityMaterialIssue issue, Map<UUID, BigDecimal> plannedByMaterial) {
        return MaterialIssueResponse.builder()
                .uuid(issue.getUuid())
                .activityUuid(issue.getActivityUuid())
                .progressUpdateUuid(issue.getProgressUpdateUuid())
                .planLineUuid(issue.getPlanLineUuid())
                .materialId(issue.getMaterialId())
                .materialName(issue.getMaterialName())
                .qty(issue.getQty())
                .plannedQty(plannedByMaterial.getOrDefault(issue.getMaterialId(), BigDecimal.ZERO))
                .status(issue.getStatus())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
