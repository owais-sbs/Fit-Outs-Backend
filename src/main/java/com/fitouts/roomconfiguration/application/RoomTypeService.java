package com.fitouts.roomconfiguration.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.fitouts.roomconfiguration.api.*;
import com.fitouts.roomconfiguration.domain.*;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.workitemconfiguration.api.WorkItemMaterialLineResponse;
import com.fitouts.workitemconfiguration.application.WorkItemPricingHelper;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterial;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterialRepository;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final CompanyService companyService;
    private final RoomMasterRepository roomMasterRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemMaterialRepository workItemMaterialRepository;

    public RoomTypeResponse create(RoomTypeCreateRequest request) {
        UUID companyId = CompanyContext.get();

        RoomMaster roomMaster = null;
        if (request.getRoomMasterId() != null) {
            roomMaster = roomMasterRepository.findByIdAndDeletedFalse(request.getRoomMasterId())
                    .orElseThrow(() -> new NotFoundException("Room master not found"));
        }

        RoomType roomType = RoomType.builder()
                .company(companyService.getCompany(companyId))
                .roomTypeName(request.getRoomTypeName())
                .roomCode(request.getRoomCode())
                .category(request.getCategory())
                .roomMaster(roomMaster)
                .description(request.getDescription())
                .build();

        if (request.getWorkItemIds() != null) {
            roomType.setWorkItems(resolveWorkItems(request.getWorkItemIds()));
        }

        RoomType saved = roomTypeRepository.save(roomType);
        return mapToResponse(saved);
    }

    public RoomTypeResponse update(UUID id, RoomTypeUpdateRequest request) {
        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));

        if (request.getRoomTypeName() != null) roomType.setRoomTypeName(request.getRoomTypeName());
        if (request.getRoomCode() != null) roomType.setRoomCode(request.getRoomCode());
        if (request.getCategory() != null) roomType.setCategory(request.getCategory());
        if (request.getRoomMasterId() != null) {
            RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(request.getRoomMasterId())
                    .orElseThrow(() -> new NotFoundException("Room master not found"));
            roomType.setRoomMaster(roomMaster);
        }
        if (request.getDescription() != null) roomType.setDescription(request.getDescription());
        if (request.getWorkItemIds() != null) {
            roomType.setWorkItems(resolveWorkItems(request.getWorkItemIds()));
        }

        RoomType updated = roomTypeRepository.save(roomType);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public RoomTypeResponse getById(UUID id) {
        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        return mapToResponse(roomType);
    }

    @Transactional(readOnly = true)
    public Page<RoomTypeResponse> list(RoomTypeFilterRequest filter, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<RoomType> spec = RoomTypeSpecification.filter(
                filter != null ? filter : new RoomTypeFilterRequest(), companyId);

        return roomTypeRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    public RoomTypeResponse activate(UUID id) {
        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        roomType.setActive(true);
        return mapToResponse(roomTypeRepository.save(roomType));
    }

    public RoomTypeResponse deactivate(UUID id) {
        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        roomType.setActive(false);
        return mapToResponse(roomTypeRepository.save(roomType));
    }

    public void softDelete(UUID id) {
        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        roomType.setDeleted(true);
        roomType.setActive(false);
        roomTypeRepository.save(roomType);
    }

    private Set<WorkItem> resolveWorkItems(List<UUID> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return new HashSet<>();
        }
        List<WorkItem> items = new ArrayList<>();
        for (UUID workItemId : workItemIds) {
            WorkItem item = workItemRepository.findByIdAndDeletedFalse(workItemId)
                    .orElseThrow(() -> new NotFoundException("Work item not found: " + workItemId));
            items.add(item);
        }
        return new HashSet<>(items);
    }

    private RoomTypeResponse mapToResponse(RoomType roomType) {
        List<RoomTypeWorkItemSummary> workItemSummaries = mapWorkItemSummaries(roomType.getWorkItems());

        List<UUID> workItemIds = workItemSummaries.stream()
                .map(RoomTypeWorkItemSummary::getId)
                .collect(Collectors.toList());

        return RoomTypeResponse.builder()
                .id(roomType.getId())
                .companyId(roomType.getCompany() != null ? roomType.getCompany().getUuid() : null)
                .roomTypeName(roomType.getRoomTypeName())
                .roomCode(roomType.getRoomCode())
                .category(roomType.getCategory())
                .roomMasterId(roomType.getRoomMaster() != null ? roomType.getRoomMaster().getId() : null)
                .roomMasterName(roomType.getRoomMaster() != null ? roomType.getRoomMaster().getName() : null)
                .description(roomType.getDescription())
                .active(roomType.getActive())
                .createdAt(roomType.getCreatedAt())
                .updatedAt(roomType.getUpdatedAt())
                .createdBy(roomType.getCreatedBy())
                .updatedBy(roomType.getUpdatedBy())
                .workItemIds(workItemIds)
                .workItems(workItemSummaries)
                .build();
    }

    private List<RoomTypeWorkItemSummary> mapWorkItemSummaries(Set<WorkItem> workItems) {
        if (workItems == null || workItems.isEmpty()) {
            return List.of();
        }
        List<UUID> workItemIds = workItems.stream().map(WorkItem::getId).toList();
        Map<UUID, List<WorkItemMaterial>> materialsByWorkItem = workItemMaterialRepository.findByWorkItemIdIn(workItemIds)
                .stream()
                .collect(Collectors.groupingBy(line -> line.getWorkItem().getId()));

        return workItems.stream()
                .map(item -> mapWorkItemSummary(item, materialsByWorkItem.getOrDefault(item.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private RoomTypeWorkItemSummary mapWorkItemSummary(WorkItem item, List<WorkItemMaterial> materialLines) {
        List<WorkItemMaterialLineResponse> lineResponses = materialLines.stream()
                .map(this::mapMaterialLine)
                .collect(Collectors.toList());

        return RoomTypeWorkItemSummary.builder()
                .id(item.getId())
                .workItemName(item.getWorkItemName())
                .workItemCode(item.getWorkItemCode())
                .workItemMasterId(item.getWorkItemMaster() != null ? item.getWorkItemMaster().getId() : null)
                .workItemMasterName(item.getWorkItemMaster() != null ? item.getWorkItemMaster().getName() : null)
                .unitType(item.getUnitType())
                .defaultRate(item.getDefaultRate())
                .subcontractorRate(item.getSubcontractorRate())
                .quantityFormulaType(item.getQuantityFormulaType())
                .ceilingApplicable(item.getCeilingApplicable())
                .wallApplicable(item.getWallApplicable())
                .floorApplicable(item.getFloorApplicable())
                .costPrice(item.getCostPrice())
                .markupPercentage(item.getMarkupPercentage())
                .materialLines(lineResponses)
                .build();
    }

    private WorkItemMaterialLineResponse mapMaterialLine(WorkItemMaterial line) {
        Material material = line.getMaterial();
        return WorkItemMaterialLineResponse.builder()
                .materialId(material.getId())
                .materialName(material.getMaterialName())
                .materialCode(material.getMaterialCode())
                .materialCategoryName(material.getMaterialCategory() != null ? material.getMaterialCategory().getName() : null)
                .unitType(material.getUnitType())
                .costPrice(material.getCostPrice())
                .quantityPerUnit(line.getQuantityPerUnit())
                .wastagePercent(line.getWastagePercent())
                .lineCost(WorkItemPricingHelper.lineCost(line))
                .build();
    }
}
