package com.fitouts.workitemconfiguration.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.procurement.domain.Material;
import com.fitouts.procurement.domain.MaterialRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.workitemconfiguration.api.*;
import com.fitouts.workitemconfiguration.domain.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkItemService {

    private final WorkItemRepository workItemRepository;
    private final CompanyService companyService;
    private final WorkItemMasterRepository workItemMasterRepository;
    private final WorkItemMaterialRepository workItemMaterialRepository;
    private final MaterialRepository materialRepository;

    public WorkItemResponse create(WorkItemCreateRequest request) {
        UUID companyId = CompanyContext.get();

        WorkItemMaster workItemMaster = null;
        if (request.getWorkItemMasterId() != null) {
            workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(request.getWorkItemMasterId())
                    .orElseThrow(() -> new NotFoundException("Work item master not found"));
        }

        boolean costOverride = Boolean.TRUE.equals(request.getCostPriceOverride());
        boolean sellingOverride = Boolean.TRUE.equals(request.getSellingPriceOverride());

        WorkItem workItem = WorkItem.builder()
                .company(companyService.getCompany(companyId))
                .workItemName(request.getWorkItemName())
                .workItemCode(request.getWorkItemCode())
                .category(request.getCategory())
                .workItemMaster(workItemMaster)
                .description(request.getDescription())
                .ceilingApplicable(request.getCeilingApplicable())
                .wallApplicable(request.getWallApplicable())
                .floorApplicable(request.getFloorApplicable())
                .unitType(request.getUnitType())
                .subcontractorRate(request.getSubcontractorRate())
                .markupPercentage(request.getMarkupPercentage())
                .quantityFormulaType(request.getQuantityFormulaType())
                .icon(request.getIcon())
                .colorTag(request.getColorTag())
                .costPriceOverride(costOverride)
                .sellingPriceOverride(sellingOverride)
                .build();

        WorkItem saved = workItemRepository.save(workItem);
        List<WorkItemMaterial> materialLines = saveMaterialLines(saved, request.getMaterialLines());
        applyPricing(saved, materialLines, request.getCostPrice(), costOverride,
                request.getDefaultRate(), sellingOverride);
        saved = workItemRepository.save(saved);
        return mapToResponse(saved, materialLines);
    }

    public WorkItemResponse update(UUID id, WorkItemUpdateRequest request) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));

        if (request.getWorkItemName() != null) workItem.setWorkItemName(request.getWorkItemName());
        if (request.getWorkItemCode() != null) workItem.setWorkItemCode(request.getWorkItemCode());
        if (request.getCategory() != null) workItem.setCategory(request.getCategory());
        if (request.getWorkItemMasterId() != null) {
            WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(request.getWorkItemMasterId())
                    .orElseThrow(() -> new NotFoundException("Work item master not found"));
            workItem.setWorkItemMaster(workItemMaster);
        }
        if (request.getDescription() != null) workItem.setDescription(request.getDescription());
        if (request.getCeilingApplicable() != null) workItem.setCeilingApplicable(request.getCeilingApplicable());
        if (request.getWallApplicable() != null) workItem.setWallApplicable(request.getWallApplicable());
        if (request.getFloorApplicable() != null) workItem.setFloorApplicable(request.getFloorApplicable());
        if (request.getUnitType() != null) workItem.setUnitType(request.getUnitType());
        if (request.getSubcontractorRate() != null) workItem.setSubcontractorRate(request.getSubcontractorRate());
        if (request.getMarkupPercentage() != null) workItem.setMarkupPercentage(request.getMarkupPercentage());
        if (request.getQuantityFormulaType() != null) workItem.setQuantityFormulaType(request.getQuantityFormulaType());
        if (request.getIcon() != null) workItem.setIcon(request.getIcon());
        if (request.getColorTag() != null) workItem.setColorTag(request.getColorTag());
        if (request.getCostPriceOverride() != null) workItem.setCostPriceOverride(request.getCostPriceOverride());
        if (request.getSellingPriceOverride() != null) workItem.setSellingPriceOverride(request.getSellingPriceOverride());

        List<WorkItemMaterial> materialLines;
        if (request.getMaterialLines() != null) {
            materialLines = saveMaterialLines(workItem, request.getMaterialLines());
        } else {
            materialLines = workItemMaterialRepository.findByWorkItemId(workItem.getId());
        }

        boolean costOverride = Boolean.TRUE.equals(workItem.getCostPriceOverride());
        boolean sellingOverride = Boolean.TRUE.equals(workItem.getSellingPriceOverride());
        BigDecimal manualCost = request.getCostPrice() != null ? request.getCostPrice() : workItem.getCostPrice();
        BigDecimal manualSelling = request.getDefaultRate() != null ? request.getDefaultRate() : workItem.getDefaultRate();
        applyPricing(workItem, materialLines, manualCost, costOverride, manualSelling, sellingOverride);

        WorkItem updated = workItemRepository.save(workItem);
        return mapToResponse(updated, materialLines);
    }

    public WorkItemResponse clone(UUID id) {
        UUID companyId = CompanyContext.get();

        WorkItem original = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));

        String clonedCode = original.getWorkItemCode() + "-CLONE";
        int suffix = 1;
        while (workItemRepository.existsByCompanyUuidAndWorkItemCodeAndDeletedFalse(companyId, clonedCode)) {
            clonedCode = original.getWorkItemCode() + "-CLONE-" + suffix;
            suffix++;
        }

        WorkItem cloned = WorkItem.builder()
                .company(original.getCompany())
                .workItemName(original.getWorkItemName() + " (Copy)")
                .workItemCode(clonedCode)
                .category(original.getCategory())
                .workItemMaster(original.getWorkItemMaster())
                .description(original.getDescription())
                .ceilingApplicable(original.getCeilingApplicable())
                .wallApplicable(original.getWallApplicable())
                .floorApplicable(original.getFloorApplicable())
                .unitType(original.getUnitType())
                .defaultRate(original.getDefaultRate())
                .subcontractorRate(original.getSubcontractorRate())
                .markupPercentage(original.getMarkupPercentage())
                .costPrice(original.getCostPrice())
                .costPriceOverride(original.getCostPriceOverride())
                .sellingPriceOverride(original.getSellingPriceOverride())
                .quantityFormulaType(original.getQuantityFormulaType())
                .icon(original.getIcon())
                .colorTag(original.getColorTag())
                .build();

        WorkItem saved = workItemRepository.save(cloned);
        List<WorkItemMaterial> originalLines = workItemMaterialRepository.findByWorkItemId(original.getId());
        List<WorkItemMaterialLineRequest> lineRequests = originalLines.stream()
                .map(line -> WorkItemMaterialLineRequest.builder()
                        .materialId(line.getMaterial().getId())
                        .quantityPerUnit(line.getQuantityPerUnit())
                        .wastagePercent(line.getWastagePercent())
                        .build())
                .collect(Collectors.toList());
        List<WorkItemMaterial> clonedLines = saveMaterialLines(saved, lineRequests);
        return mapToResponse(saved, clonedLines);
    }

    @Transactional(readOnly = true)
    public WorkItemResponse getById(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        List<WorkItemMaterial> materialLines = workItemMaterialRepository.findByWorkItemId(id);
        return mapToResponse(workItem, materialLines);
    }

    @Transactional(readOnly = true)
    public Page<WorkItemResponse> list(WorkItemFilterRequest filter, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<WorkItem> spec = WorkItemSpecification.filter(
                filter != null ? filter : new WorkItemFilterRequest(), companyId);

        return workItemRepository.findAll(spec, pageable).map(wi -> {
            List<WorkItemMaterial> lines = workItemMaterialRepository.findByWorkItemId(wi.getId());
            return mapToResponse(wi, lines);
        });
    }

    @Transactional(readOnly = true)
    public Page<WorkItemResponse> listBySurfaceType(String surfaceType, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("workItemName").ascending());

        Specification<WorkItem> spec = WorkItemSpecification.filterBySurfaceType(surfaceType, companyId);

        return workItemRepository.findAll(spec, pageable).map(wi -> {
            List<WorkItemMaterial> lines = workItemMaterialRepository.findByWorkItemId(wi.getId());
            return mapToResponse(wi, lines);
        });
    }

    public WorkItemResponse activate(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setActive(true);
        WorkItem saved = workItemRepository.save(workItem);
        return mapToResponse(saved, workItemMaterialRepository.findByWorkItemId(id));
    }

    public WorkItemResponse deactivate(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setActive(false);
        WorkItem saved = workItemRepository.save(workItem);
        return mapToResponse(saved, workItemMaterialRepository.findByWorkItemId(id));
    }

    public void softDelete(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setDeleted(true);
        workItem.setActive(false);
        workItemRepository.save(workItem);
    }

    private List<WorkItemMaterial> saveMaterialLines(WorkItem workItem, List<WorkItemMaterialLineRequest> requests) {
        workItemMaterialRepository.deleteByWorkItemId(workItem.getId());
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<WorkItemMaterial> saved = new ArrayList<>();
        for (WorkItemMaterialLineRequest line : requests) {
            if (line.getMaterialId() == null) {
                continue;
            }
            Material material = materialRepository.findByIdAndDeletedFalse(line.getMaterialId())
                    .orElseThrow(() -> new NotFoundException("Material not found: " + line.getMaterialId()));
            WorkItemMaterial entity = WorkItemMaterial.builder()
                    .workItem(workItem)
                    .material(material)
                    .quantityPerUnit(line.getQuantityPerUnit() != null ? line.getQuantityPerUnit() : BigDecimal.ONE)
                    .wastagePercent(line.getWastagePercent() != null ? line.getWastagePercent() : BigDecimal.ZERO)
                    .build();
            saved.add(workItemMaterialRepository.save(entity));
        }
        return saved;
    }

    private void applyPricing(WorkItem workItem, List<WorkItemMaterial> materialLines,
            BigDecimal manualCost, boolean costOverride, BigDecimal manualSelling, boolean sellingOverride) {
        if (costOverride && manualCost != null) {
            workItem.setCostPrice(manualCost);
        } else {
            workItem.setCostPrice(WorkItemPricingHelper.calculateMaterialCost(materialLines));
        }

        if (sellingOverride && manualSelling != null) {
            workItem.setDefaultRate(manualSelling);
        } else {
            BigDecimal cost = workItem.getCostPrice() != null ? workItem.getCostPrice() : BigDecimal.ZERO;
            BigDecimal markup = workItem.getMarkupPercentage() != null ? workItem.getMarkupPercentage() : BigDecimal.ZERO;
            BigDecimal factor = BigDecimal.ONE.add(markup.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            workItem.setDefaultRate(cost.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private WorkItemResponse mapToResponse(WorkItem workItem, List<WorkItemMaterial> materialLines) {
        List<WorkItemMaterialLineResponse> lineResponses = materialLines == null ? List.of() : materialLines.stream()
                .map(this::mapMaterialLine)
                .collect(Collectors.toList());

        return WorkItemResponse.builder()
                .id(workItem.getId())
                .companyId(workItem.getCompany() != null ? workItem.getCompany().getUuid() : null)
                .workItemName(workItem.getWorkItemName())
                .workItemCode(workItem.getWorkItemCode())
                .category(workItem.getCategory())
                .workItemMasterId(workItem.getWorkItemMaster() != null ? workItem.getWorkItemMaster().getId() : null)
                .workItemMasterName(workItem.getWorkItemMaster() != null ? workItem.getWorkItemMaster().getName() : null)
                .description(workItem.getDescription())
                .ceilingApplicable(workItem.getCeilingApplicable())
                .wallApplicable(workItem.getWallApplicable())
                .floorApplicable(workItem.getFloorApplicable())
                .unitType(workItem.getUnitType())
                .defaultRate(workItem.getDefaultRate())
                .subcontractorRate(workItem.getSubcontractorRate())
                .markupPercentage(workItem.getMarkupPercentage())
                .costPrice(workItem.getCostPrice())
                .sellingPriceOverride(workItem.getSellingPriceOverride())
                .costPriceOverride(workItem.getCostPriceOverride())
                .materialLines(lineResponses)
                .quantityFormulaType(workItem.getQuantityFormulaType())
                .icon(workItem.getIcon())
                .colorTag(workItem.getColorTag())
                .active(workItem.getActive())
                .createdAt(workItem.getCreatedAt())
                .updatedAt(workItem.getUpdatedAt())
                .createdBy(workItem.getCreatedBy())
                .updatedBy(workItem.getUpdatedBy())
                .build();
    }

    private WorkItemMaterialLineResponse mapMaterialLine(WorkItemMaterial line) {
        Material material = line.getMaterial();
        BigDecimal lineCost = WorkItemPricingHelper.lineCost(line);
        return WorkItemMaterialLineResponse.builder()
                .materialId(material.getId())
                .materialName(material.getMaterialName())
                .materialCode(material.getMaterialCode())
                .materialCategoryName(material.getMaterialCategory() != null ? material.getMaterialCategory().getName() : null)
                .unitType(material.getUnitType())
                .costPrice(material.getCostPrice())
                .quantityPerUnit(line.getQuantityPerUnit())
                .wastagePercent(line.getWastagePercent())
                .lineCost(lineCost)
                .build();
    }
}

