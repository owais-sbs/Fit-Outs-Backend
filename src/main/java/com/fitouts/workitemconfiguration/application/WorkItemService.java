package com.fitouts.workitemconfiguration.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.shared.context.CompanyContext;
// import com.fitouts.shared.error.BadRequestException;
// import com.fitouts.shared.error.ConflictException;
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

    public WorkItemResponse create(WorkItemCreateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        // if (workItemRepository.existsByCompanyUuidAndWorkItemCodeAndDeletedFalse(companyId, request.getWorkItemCode())) {
        //     throw new ConflictException("Work item with code '" + request.getWorkItemCode() + "' already exists");
        // }

        WorkItemMaster workItemMaster = null;
        if (request.getWorkItemMasterId() != null) {
            workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(request.getWorkItemMasterId())
                    .orElseThrow(() -> new NotFoundException("Work item master not found"));
        }

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
                .defaultRate(request.getDefaultRate())
                .subcontractorRate(request.getSubcontractorRate())
                .markupPercentage(request.getMarkupPercentage())
                .quantityFormulaType(request.getQuantityFormulaType())
                .icon(request.getIcon())
                .colorTag(request.getColorTag())
                .build();

        WorkItem saved = workItemRepository.save(workItem);
        return mapToResponse(saved);
    }

    public WorkItemResponse update(UUID id, WorkItemUpdateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));

        // if (!workItem.getCompany().getUuid().equals(companyId)) {
        //     throw new BadRequestException("Work item does not belong to current company");
        // }

        // if (request.getWorkItemCode() != null
        //         && workItemRepository.existsByCompanyUuidAndWorkItemCodeAndIdNotAndDeletedFalse(
        //                 companyId, request.getWorkItemCode(), id)) {
        //     throw new ConflictException("Work item with code '" + request.getWorkItemCode() + "' already exists");
        // }

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
        if (request.getDefaultRate() != null) workItem.setDefaultRate(request.getDefaultRate());
        if (request.getSubcontractorRate() != null) workItem.setSubcontractorRate(request.getSubcontractorRate());
        if (request.getMarkupPercentage() != null) workItem.setMarkupPercentage(request.getMarkupPercentage());
        if (request.getQuantityFormulaType() != null) workItem.setQuantityFormulaType(request.getQuantityFormulaType());
        if (request.getIcon() != null) workItem.setIcon(request.getIcon());
        if (request.getColorTag() != null) workItem.setColorTag(request.getColorTag());

        WorkItem updated = workItemRepository.save(workItem);
        return mapToResponse(updated);
    }

    public WorkItemResponse clone(UUID id) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        WorkItem original = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));

        // if (!original.getCompany().getUuid().equals(companyId)) {
        //     throw new BadRequestException("Work item does not belong to current company");
        // }

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
                .quantityFormulaType(original.getQuantityFormulaType())
                .icon(original.getIcon())
                .colorTag(original.getColorTag())
                .build();

        WorkItem saved = workItemRepository.save(cloned);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public WorkItemResponse getById(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        return mapToResponse(workItem);
    }

    @Transactional(readOnly = true)
    public Page<WorkItemResponse> list(WorkItemFilterRequest filter, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<WorkItem> spec = WorkItemSpecification.filter(
                filter != null ? filter : new WorkItemFilterRequest(), companyId);

        return workItemRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<WorkItemResponse> listBySurfaceType(String surfaceType, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("workItemName").ascending());

        Specification<WorkItem> spec = WorkItemSpecification.filterBySurfaceType(surfaceType, companyId);

        return workItemRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    public WorkItemResponse activate(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setActive(true);
        return mapToResponse(workItemRepository.save(workItem));
    }

    public WorkItemResponse deactivate(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setActive(false);
        return mapToResponse(workItemRepository.save(workItem));
    }

    public void softDelete(UUID id) {
        WorkItem workItem = workItemRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item not found"));
        workItem.setDeleted(true);
        workItem.setActive(false);
        workItemRepository.save(workItem);
    }

    private WorkItemResponse mapToResponse(WorkItem workItem) {
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
}
