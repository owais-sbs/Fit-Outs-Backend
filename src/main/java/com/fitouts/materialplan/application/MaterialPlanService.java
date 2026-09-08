package com.fitouts.materialplan.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.fitouts.materialplan.api.MaterialPlanWorkItemSectionResponse;
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
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterial;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterialRepository;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

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
    private final WorkItemRepository workItemRepository;
    private final MaterialStockRepository materialStockRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public MaterialPlanResponse get(Long projectId) {
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return planRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .map(plan -> toResponse(plan, List.of()))
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

        List<String> warnings = new ArrayList<>();
        BoqDocument approvedBoq = findLatestApprovedBoq(project.getId(), companyId);
        List<ProjectMaterialPlanLine> lines = new ArrayList<>();
        if (approvedBoq != null) {
            lines = buildLinesFromBoq(plan.getUuid(), approvedBoq, companyId, warnings);
            plan.setGeneratedFromBoqId(approvedBoq.getId());
            if (lines.isEmpty() && warnings.isEmpty()) {
                warnings.add(
                        "No materials could be derived. Link BOQ lines to work items (re-save the BOQ from QAS) and ensure each work item has materials configured in Project Configuration.");
            }
        } else {
            plan.setGeneratedFromBoqId(null);
            warnings.add("No approved BOQ found for this project. Complete client approval on the BOQ first.");
        }

        plan.setStatus(MaterialPlanStatus.DRAFT);
        plan.setUpdatedBy(principal.getAccountId());
        plan = planRepository.save(plan);
        if (!lines.isEmpty()) {
            lineRepository.saveAll(lines);
        }

        syncPlanningMaterialStatus(project.getId(), plan.getStatus(), lines.size(), principal.getAccountId());
        return toResponse(plan, warnings);
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
                lines.add(toLineEntity(plan.getUuid(), lr, order++, companyId));
            }
            if (!lines.isEmpty()) {
                lineRepository.saveAll(lines);
            }
        }

        plan.setUpdatedBy(principal.getAccountId());
        plan = planRepository.save(plan);

        int lineCount = lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid()).size();
        syncPlanningMaterialStatus(project.getId(), plan.getStatus(), lineCount, principal.getAccountId());
        return toResponse(plan, List.of());
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
        return toResponse(plan, List.of());
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        StringBuilder sb = new StringBuilder();
        sb.append("materialName,workItemNames,plannedQty,stockQtySnapshot,unit,shortageFlag,reservedQty,substituteReason,notes,sortOrder\n");
        planRepository.findByProjectIdAndCompanyId(project.getId(), companyId).ifPresent(plan -> {
            for (ProjectMaterialPlanLine line : lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid())) {
                sb.append(csv(line.getMaterialName())).append(',')
                        .append(csv(line.getWorkItemNames())).append(',')
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

    private List<ProjectMaterialPlanLine> buildLinesFromBoq(
            UUID planUuid, BoqDocument boq, UUID companyId, List<String> warnings) {
        List<BoqLine> boqLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(boq.getId());
        if (boqLines.isEmpty()) {
            warnings.add("The approved BOQ has no line items.");
            return List.of();
        }

        List<WorkItem> companyWorkItems = workItemRepository.findByCompanyUuidAndDeletedFalse(companyId);
        Map<String, UUID> workItemByName = companyWorkItems.stream()
                .filter(wi -> StringUtils.hasText(wi.getWorkItemName()))
                .collect(Collectors.toMap(
                        wi -> normalizeWorkItemName(wi.getWorkItemName()),
                        WorkItem::getId,
                        (a, b) -> a));

        Map<UUID, String> workItemNameById = companyWorkItems.stream()
                .filter(wi -> StringUtils.hasText(wi.getWorkItemName()))
                .collect(Collectors.toMap(
                        WorkItem::getId,
                        wi -> wi.getWorkItemName().trim(),
                        (a, b) -> a));

        LinkedHashMap<String, BoqWorkItemRef> workItemsInOrder = new LinkedHashMap<>();
        for (BoqLine boqLine : boqLines) {
            BoqWorkItemRef ref = resolveBoqWorkItemRef(boqLine, workItemByName, workItemNameById);
            if (ref == null) {
                continue;
            }
            workItemsInOrder.putIfAbsent(ref.key(), ref);
        }

        if (workItemsInOrder.isEmpty()) {
            warnings.add("BOQ lines are not linked to work items. Re-save the BOQ from QAS so work items are stored on each line.");
            return List.of();
        }

        List<UUID> workItemIds = workItemsInOrder.values().stream()
                .map(BoqWorkItemRef::workItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, List<WorkItemMaterial>> byWorkItem = workItemIds.isEmpty()
                ? Map.of()
                : workItemMaterialRepository.findByWorkItemIdIn(workItemIds).stream()
                        .collect(Collectors.groupingBy(wim -> wim.getWorkItem().getId()));

        Map<String, Map<UUID, AggregatedMaterial>> materialByWorkItemKey = new HashMap<>();
        for (BoqLine boqLine : boqLines) {
            BoqWorkItemRef ref = resolveBoqWorkItemRef(boqLine, workItemByName, workItemNameById);
            if (ref == null) {
                continue;
            }
            UUID workItemId = ref.workItemId();
            List<WorkItemMaterial> mats = workItemId != null
                    ? byWorkItem.getOrDefault(workItemId, List.of())
                    : List.of();
            BigDecimal boqQty = boqLine.getQuantity() != null ? boqLine.getQuantity() : BigDecimal.ZERO;
            Map<UUID, AggregatedMaterial> aggregatedForWorkItem = materialByWorkItemKey.computeIfAbsent(
                    ref.key(), key -> new HashMap<>());

            for (WorkItemMaterial wim : mats) {
                Material material = wim.getMaterial();
                if (material == null || Boolean.TRUE.equals(material.getDeleted())) {
                    continue;
                }
                BigDecimal perUnit = wim.getQuantityPerUnit() != null ? wim.getQuantityPerUnit() : BigDecimal.ONE;
                BigDecimal wastage = wim.getWastagePercent() != null ? wim.getWastagePercent() : BigDecimal.ZERO;
                BigDecimal factor = BigDecimal.ONE.add(wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                BigDecimal planned = boqQty.multiply(perUnit).multiply(factor).setScale(4, RoundingMode.HALF_UP);

                AggregatedMaterial agg = aggregatedForWorkItem.computeIfAbsent(material.getId(), id -> {
                    AggregatedMaterial a = new AggregatedMaterial();
                    a.materialId = material.getId();
                    a.materialName = material.getMaterialName();
                    a.unit = material.getUnitType() != null ? material.getUnitType().name() : null;
                    a.plannedQty = BigDecimal.ZERO;
                    a.workItemNames = new LinkedHashSet<>();
                    return a;
                });
                agg.plannedQty = agg.plannedQty.add(planned);
                if (StringUtils.hasText(ref.label())) {
                    agg.workItemNames.add(ref.label().trim());
                }
            }
        }

        int emptyWorkItems = 0;
        for (BoqWorkItemRef ref : workItemsInOrder.values()) {
            Map<UUID, AggregatedMaterial> materials = materialByWorkItemKey.getOrDefault(ref.key(), Map.of());
            if (materials.isEmpty()) {
                emptyWorkItems++;
                warnings.add("Work item \"" + ref.label() + "\" has no materials configured.");
            }
        }

        Set<UUID> allMaterialIds = materialByWorkItemKey.values().stream()
                .flatMap(map -> map.keySet().stream())
                .collect(Collectors.toSet());

        if (allMaterialIds.isEmpty()) {
            if (emptyWorkItems == workItemsInOrder.size()) {
                warnings.add("Work items on the BOQ have no material mappings. Add materials in Project Configuration → Work Items.");
            }
            return List.of();
        }

        Map<UUID, BigDecimal> stockByMaterial = materialStockRepository
                .findByCompanyUuidAndMaterialIdIn(companyId, allMaterialIds)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getMaterial().getId(),
                        MaterialStock::getQuantityOnHand,
                        (a, b) -> a));

        List<ProjectMaterialPlanLine> lines = new ArrayList<>();
        int order = 0;
        for (BoqWorkItemRef ref : workItemsInOrder.values()) {
            Map<UUID, AggregatedMaterial> aggregatedForWorkItem = materialByWorkItemKey.getOrDefault(ref.key(), Map.of());
            for (AggregatedMaterial agg : aggregatedForWorkItem.values()) {
                BigDecimal stock = stockByMaterial.getOrDefault(agg.materialId, BigDecimal.ZERO);
                ProjectMaterialPlanLine line = new ProjectMaterialPlanLine();
                line.setPlanUuid(planUuid);
                line.setMaterialId(agg.materialId);
                line.setMaterialName(agg.materialName);
                line.setWorkItemNames(joinWorkItemNames(Set.of(ref.label())));
                line.setPlannedQty(agg.plannedQty);
                line.setStockQtySnapshot(stock);
                line.setUnit(agg.unit);
                line.setShortageFlag(agg.plannedQty.compareTo(stock) > 0);
                line.setReservedQty(BigDecimal.ZERO);
                line.setSortOrder(order++);
                lines.add(line);
            }
        }
        return lines;
    }

    private BoqWorkItemRef resolveBoqWorkItemRef(
            BoqLine boqLine, Map<String, UUID> workItemByName, Map<UUID, String> workItemNameById) {
        UUID workItemId = resolveWorkItemId(boqLine, workItemByName);
        String label = resolveWorkItemLabel(boqLine, workItemId, workItemNameById);
        if (!StringUtils.hasText(label)) {
            return null;
        }
        String key = workItemId != null ? "id:" + workItemId : "desc:" + normalizeWorkItemName(label);
        return new BoqWorkItemRef(workItemId, label.trim(), key);
    }

    private String resolveWorkItemLabel(BoqLine boqLine, UUID workItemId, Map<UUID, String> workItemNameById) {
        if (workItemId != null && workItemNameById.containsKey(workItemId)) {
            return workItemNameById.get(workItemId);
        }
        if (StringUtils.hasText(boqLine.getDescription())) {
            return boqLine.getDescription().trim();
        }
        return null;
    }

    private List<MaterialPlanWorkItemSectionResponse> buildSections(
            Long projectId, UUID companyId, UUID generatedFromBoqId, List<MaterialPlanLineResponse> lineResponses) {
        BoqDocument boq = generatedFromBoqId != null
                ? boqDocumentRepository.findById(generatedFromBoqId).orElse(null)
                : findLatestApprovedBoq(projectId, companyId);

        if (boq == null) {
            return groupLineResponsesIntoSections(lineResponses);
        }

        List<BoqLine> boqLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(boq.getId());
        if (boqLines.isEmpty()) {
            return groupLineResponsesIntoSections(lineResponses);
        }

        List<WorkItem> companyWorkItems = workItemRepository.findByCompanyUuidAndDeletedFalse(companyId);
        Map<String, UUID> workItemByName = companyWorkItems.stream()
                .filter(wi -> StringUtils.hasText(wi.getWorkItemName()))
                .collect(Collectors.toMap(
                        wi -> normalizeWorkItemName(wi.getWorkItemName()),
                        WorkItem::getId,
                        (a, b) -> a));
        Map<UUID, String> workItemNameById = companyWorkItems.stream()
                .filter(wi -> StringUtils.hasText(wi.getWorkItemName()))
                .collect(Collectors.toMap(
                        WorkItem::getId,
                        wi -> wi.getWorkItemName().trim(),
                        (a, b) -> a));

        LinkedHashMap<String, BoqWorkItemRef> workItemsInOrder = new LinkedHashMap<>();
        for (BoqLine boqLine : boqLines) {
            BoqWorkItemRef ref = resolveBoqWorkItemRef(boqLine, workItemByName, workItemNameById);
            if (ref == null) {
                continue;
            }
            workItemsInOrder.putIfAbsent(ref.key(), ref);
        }

        if (workItemsInOrder.isEmpty()) {
            return groupLineResponsesIntoSections(lineResponses);
        }

        Map<String, List<MaterialPlanLineResponse>> linesByWorkItemKey = new HashMap<>();
        List<MaterialPlanLineResponse> unnamedLines = new ArrayList<>();
        for (MaterialPlanLineResponse line : lineResponses) {
            List<String> names = line.getWorkItemNames() != null ? line.getWorkItemNames() : List.of();
            if (names.isEmpty()) {
                unnamedLines.add(line);
                continue;
            }
            for (String name : names) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String normalized = normalizeWorkItemName(name);
                linesByWorkItemKey.computeIfAbsent("desc:" + normalized, key -> new ArrayList<>()).add(line);
                UUID mappedId = workItemByName.get(normalized);
                if (mappedId == null) {
                    mappedId = matchWorkItemIdByName(name, workItemByName);
                }
                if (mappedId != null) {
                    linesByWorkItemKey.computeIfAbsent("id:" + mappedId, key -> new ArrayList<>()).add(line);
                }
            }
        }

        Map<UUID, List<BoqWorkItemRef>> workItemsByMaterialId = mapWorkItemsByMaterialId(workItemsInOrder.values());
        Set<UUID> placedLineIds = new HashSet<>();
        Map<String, LinkedHashSet<MaterialPlanLineResponse>> sectionLines = new LinkedHashMap<>();
        for (BoqWorkItemRef ref : workItemsInOrder.values()) {
            sectionLines.put(ref.key(), new LinkedHashSet<>());
        }

        for (BoqWorkItemRef ref : workItemsInOrder.values()) {
            LinkedHashSet<MaterialPlanLineResponse> bucket = sectionLines.get(ref.key());
            String descKey = "desc:" + normalizeWorkItemName(ref.label());
            String idKey = ref.workItemId() != null ? "id:" + ref.workItemId() : null;
            if (idKey != null) {
                bucket.addAll(linesByWorkItemKey.getOrDefault(idKey, List.of()));
            }
            bucket.addAll(linesByWorkItemKey.getOrDefault(descKey, List.of()));
            bucket.forEach(line -> {
                if (line.getUuid() != null) {
                    placedLineIds.add(line.getUuid());
                }
            });
        }

        for (MaterialPlanLineResponse line : unnamedLines) {
            List<BoqWorkItemRef> owners = line.getMaterialId() != null
                    ? workItemsByMaterialId.getOrDefault(line.getMaterialId(), List.of())
                    : List.of();
            if (owners.isEmpty()) {
                continue;
            }
            for (BoqWorkItemRef ref : owners) {
                sectionLines.get(ref.key()).add(line);
            }
            if (line.getUuid() != null) {
                placedLineIds.add(line.getUuid());
            }
        }

        List<MaterialPlanWorkItemSectionResponse> sections = new ArrayList<>();
        int sortOrder = 0;
        for (BoqWorkItemRef ref : workItemsInOrder.values()) {
            sections.add(MaterialPlanWorkItemSectionResponse.builder()
                    .workItemId(ref.workItemId())
                    .workItemName(ref.label())
                    .sortOrder(sortOrder++)
                    .lines(new ArrayList<>(sectionLines.getOrDefault(ref.key(), new LinkedHashSet<>())))
                    .build());
        }

        List<MaterialPlanLineResponse> unassigned = new ArrayList<>();
        for (MaterialPlanLineResponse line : lineResponses) {
            if (line.getUuid() != null && placedLineIds.contains(line.getUuid())) {
                continue;
            }
            if (line.getUuid() == null && sectionLines.values().stream().anyMatch(set -> set.contains(line))) {
                continue;
            }
            unassigned.add(line);
        }
        if (!unassigned.isEmpty()) {
            sections.add(MaterialPlanWorkItemSectionResponse.builder()
                    .workItemId(null)
                    .workItemName("Unassigned materials")
                    .sortOrder(sortOrder)
                    .lines(unassigned)
                    .build());
        }

        return sections;
    }

    private Map<UUID, List<BoqWorkItemRef>> mapWorkItemsByMaterialId(Collection<BoqWorkItemRef> workItems) {
        List<UUID> workItemIds = workItems.stream()
                .map(BoqWorkItemRef::workItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (workItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BoqWorkItemRef> refById = workItems.stream()
                .filter(ref -> ref.workItemId() != null)
                .collect(Collectors.toMap(BoqWorkItemRef::workItemId, ref -> ref, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, List<BoqWorkItemRef>> byMaterial = new HashMap<>();
        for (WorkItemMaterial wim : workItemMaterialRepository.findByWorkItemIdIn(workItemIds)) {
            if (wim.getMaterial() == null || wim.getWorkItem() == null) {
                continue;
            }
            BoqWorkItemRef ref = refById.get(wim.getWorkItem().getId());
            if (ref == null) {
                continue;
            }
            byMaterial.computeIfAbsent(wim.getMaterial().getId(), key -> new ArrayList<>()).add(ref);
        }
        return byMaterial;
    }

    private List<MaterialPlanWorkItemSectionResponse> groupLineResponsesIntoSections(
            List<MaterialPlanLineResponse> lineResponses) {
        if (lineResponses == null || lineResponses.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, List<MaterialPlanLineResponse>> grouped = new LinkedHashMap<>();
        for (MaterialPlanLineResponse line : lineResponses) {
            List<String> names = line.getWorkItemNames() != null ? line.getWorkItemNames() : List.of();
            String key = names.isEmpty()
                    ? "__unassigned__"
                    : names.stream().sorted().collect(Collectors.joining("\0"));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }

        List<MaterialPlanWorkItemSectionResponse> sections = new ArrayList<>();
        int sortOrder = 0;
        for (Map.Entry<String, List<MaterialPlanLineResponse>> entry : grouped.entrySet()) {
            String label = "__unassigned__".equals(entry.getKey())
                    ? "Unassigned materials"
                    : entry.getValue().stream()
                            .flatMap(line -> (line.getWorkItemNames() != null ? line.getWorkItemNames() : List.<String>of()).stream())
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .distinct()
                            .sorted()
                            .collect(Collectors.joining(" · "));
            sections.add(MaterialPlanWorkItemSectionResponse.builder()
                    .workItemId(null)
                    .workItemName(label)
                    .sortOrder(sortOrder++)
                    .lines(entry.getValue())
                    .build());
        }
        return sections;
    }

    private record BoqWorkItemRef(UUID workItemId, String label, String key) {}

    private UUID resolveWorkItemId(BoqLine boqLine, Map<String, UUID> workItemByName) {
        if (boqLine.getWorkItem() != null) {
            return boqLine.getWorkItem().getId();
        }
        if (boqLine.getQtoLine() != null && boqLine.getQtoLine().getWorkItem() != null) {
            return boqLine.getQtoLine().getWorkItem().getId();
        }
        return matchWorkItemIdByName(boqLine.getDescription(), workItemByName);
    }

    private UUID matchWorkItemIdByName(String raw, Map<String, UUID> workItemByName) {
        if (!StringUtils.hasText(raw) || workItemByName == null || workItemByName.isEmpty()) {
            return null;
        }
        String needle = normalizeWorkItemName(raw);
        UUID exact = workItemByName.get(needle);
        if (exact != null) {
            return exact;
        }
        UUID best = null;
        int bestLen = -1;
        for (Map.Entry<String, UUID> entry : workItemByName.entrySet()) {
            String name = entry.getKey();
            if (name.isBlank()) {
                continue;
            }
            boolean match = needle.equals(name) || needle.startsWith(name) || name.startsWith(needle);
            if (!match) {
                continue;
            }
            if (name.length() > bestLen) {
                best = entry.getValue();
                bestLen = name.length();
            }
        }
        return best;
    }

    private static String normalizeWorkItemName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim().toLowerCase().replaceAll("\\s+", " ");
        if (value.endsWith("at:")) {
            value = value.substring(0, value.length() - 3).trim();
        }
        return value;
    }

    private BoqDocument findLatestApprovedBoq(Long projectId, UUID companyId) {
        return boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(d -> companyId.equals(d.getCompanyId()))
                .filter(d -> d.getStatus() == BoqDocumentStatus.APPROVED || d.getStatus() == BoqDocumentStatus.FINAL)
                .findFirst()
                .orElse(null);
    }

    private ProjectMaterialPlanLine toLineEntity(UUID planUuid, MaterialPlanLineRequest lr, int defaultOrder, UUID companyId) {
        if (!StringUtils.hasText(lr.getMaterialName())) {
            throw new BadRequestException("materialName is required for each line");
        }
        BigDecimal planned = lr.getPlannedQty() != null ? lr.getPlannedQty() : BigDecimal.ZERO;
        BigDecimal stock = resolveStockSnapshot(companyId, lr.getMaterialId(), lr.getStockQtySnapshot());
        boolean shortage = planned.compareTo(stock) > 0;

        ProjectMaterialPlanLine line = new ProjectMaterialPlanLine();
        line.setPlanUuid(planUuid);
        line.setMaterialId(lr.getMaterialId());
        line.setMaterialName(lr.getMaterialName().trim());
        line.setWorkItemNames(joinWorkItemNames(lr.getWorkItemNames()));
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

    private BigDecimal resolveStockSnapshot(UUID companyId, UUID materialId, BigDecimal fallback) {
        if (materialId == null || companyId == null) {
            return fallback != null ? fallback : BigDecimal.ZERO;
        }
        return materialStockRepository
                .findByCompanyUuidAndMaterialIdIn(companyId, List.of(materialId))
                .stream()
                .findFirst()
                .map(MaterialStock::getQuantityOnHand)
                .orElse(fallback != null ? fallback : BigDecimal.ZERO);
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

    private MaterialPlanResponse toResponse(ProjectMaterialPlan plan, List<String> warnings) {
        List<MaterialPlanLineResponse> lines = lineRepository.findByPlanUuidOrderBySortOrderAsc(plan.getUuid())
                .stream()
                .map(this::toLineResponse)
                .toList();
        List<MaterialPlanWorkItemSectionResponse> sections = buildSections(
                plan.getProjectId(), plan.getCompanyId(), plan.getGeneratedFromBoqId(), lines);
        return MaterialPlanResponse.builder()
                .uuid(plan.getUuid())
                .projectId(plan.getProjectId())
                .companyId(plan.getCompanyId())
                .status(plan.getStatus())
                .generatedFromBoqId(plan.getGeneratedFromBoqId())
                .updatedBy(plan.getUpdatedBy())
                .updatedAt(plan.getUpdatedAt())
                .lines(lines)
                .sections(sections)
                .warnings(warnings != null ? warnings : List.of())
                .build();
    }

    private MaterialPlanLineResponse toLineResponse(ProjectMaterialPlanLine line) {
        return MaterialPlanLineResponse.builder()
                .uuid(line.getUuid())
                .materialId(line.getMaterialId())
                .materialName(line.getMaterialName())
                .workItemNames(splitWorkItemNames(line.getWorkItemNames()))
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
        BoqDocument approvedBoq = findLatestApprovedBoq(projectId, companyId);
        List<MaterialPlanWorkItemSectionResponse> sections = approvedBoq != null
                ? buildSections(projectId, companyId, approvedBoq.getId(), List.of())
                : List.of();
        return MaterialPlanResponse.builder()
                .uuid(null)
                .projectId(projectId)
                .companyId(companyId)
                .status(MaterialPlanStatus.DRAFT)
                .generatedFromBoqId(approvedBoq != null ? approvedBoq.getId() : null)
                .updatedBy(null)
                .updatedAt(null)
                .lines(List.of())
                .sections(sections)
                .warnings(List.of())
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

    private static String joinWorkItemNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        return names.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static List<String> splitWorkItemNames(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static class AggregatedMaterial {
        UUID materialId;
        String materialName;
        String unit;
        BigDecimal plannedQty;
        Set<String> workItemNames;
    }
}
