package com.fitouts.roomconfiguration.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.roomconfiguration.api.*;
import com.fitouts.roomconfiguration.domain.*;
import com.fitouts.shared.context.CompanyContext;
// import com.fitouts.shared.error.BadRequestException;
// import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final WorkItemRepository workItemRepository;
    private final CompanyService companyService;
    private final RoomMasterRepository roomMasterRepository;

    public RoomTypeResponse create(RoomTypeCreateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        // if (roomTypeRepository.existsByCompanyUuidAndRoomCodeAndDeletedFalse(companyId, request.getRoomCode())) {
        //     throw new ConflictException("Room type with code '" + request.getRoomCode() + "' already exists");
        // }

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
                .ceilingMeasurementRequired(request.getCeilingMeasurementRequired())
                .wallMeasurementRequired(request.getWallMeasurementRequired())
                .floorMeasurementRequired(request.getFloorMeasurementRequired())
                .workItems(new HashSet<>())
                .build();

        if (request.getWorkItemIds() != null && !request.getWorkItemIds().isEmpty()) {
            Set<WorkItem> workItems = new HashSet<>(workItemRepository.findAllById(request.getWorkItemIds()));
            roomType.setWorkItems(workItems);
        }

        RoomType saved = roomTypeRepository.save(roomType);
        return mapToResponse(saved);
    }

    public RoomTypeResponse update(UUID id, RoomTypeUpdateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        RoomType roomType = roomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room type not found"));

        // if (!roomType.getCompany().getUuid().equals(companyId)) {
        //     throw new BadRequestException("Room type does not belong to current company");
        // }

        // if (request.getRoomCode() != null
        //         && roomTypeRepository.existsByCompanyUuidAndRoomCodeAndIdNotAndDeletedFalse(
        //                 companyId, request.getRoomCode(), id)) {
        //     throw new ConflictException("Room type with code '" + request.getRoomCode() + "' already exists");
        // }

        if (request.getRoomTypeName() != null) roomType.setRoomTypeName(request.getRoomTypeName());
        if (request.getRoomCode() != null) roomType.setRoomCode(request.getRoomCode());
        if (request.getCategory() != null) roomType.setCategory(request.getCategory());
        if (request.getRoomMasterId() != null) {
            RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(request.getRoomMasterId())
                    .orElseThrow(() -> new NotFoundException("Room master not found"));
            roomType.setRoomMaster(roomMaster);
        }
        if (request.getDescription() != null) roomType.setDescription(request.getDescription());
        if (request.getCeilingMeasurementRequired() != null) roomType.setCeilingMeasurementRequired(request.getCeilingMeasurementRequired());
        if (request.getWallMeasurementRequired() != null) roomType.setWallMeasurementRequired(request.getWallMeasurementRequired());
        if (request.getFloorMeasurementRequired() != null) roomType.setFloorMeasurementRequired(request.getFloorMeasurementRequired());

        if (request.getWorkItemIds() != null) {
            Set<WorkItem> workItems = new HashSet<>(workItemRepository.findAllById(request.getWorkItemIds()));
            roomType.setWorkItems(workItems);
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

    private RoomTypeResponse mapToResponse(RoomType roomType) {
        Set<RoomTypeResponse.WorkItemSummaryResponse> workItemSummaries = new HashSet<>();
        if (roomType.getWorkItems() != null) {
            workItemSummaries = roomType.getWorkItems().stream()
                    .filter(wi -> !wi.getDeleted())
                    .map(wi -> RoomTypeResponse.WorkItemSummaryResponse.builder()
                            .id(wi.getId())
                            .workItemName(wi.getWorkItemName())
                            .workItemCode(wi.getWorkItemCode())
                            .icon(wi.getIcon())
                            .colorTag(wi.getColorTag())
                            .build())
                    .collect(HashSet::new, HashSet::add, HashSet::addAll);
        }

        return RoomTypeResponse.builder()
                .id(roomType.getId())
                .companyId(roomType.getCompany() != null ? roomType.getCompany().getUuid() : null)
                .roomTypeName(roomType.getRoomTypeName())
                .roomCode(roomType.getRoomCode())
                .category(roomType.getCategory())
                .roomMasterId(roomType.getRoomMaster() != null ? roomType.getRoomMaster().getId() : null)
                .roomMasterName(roomType.getRoomMaster() != null ? roomType.getRoomMaster().getName() : null)
                .description(roomType.getDescription())
                .ceilingMeasurementRequired(roomType.getCeilingMeasurementRequired())
                .wallMeasurementRequired(roomType.getWallMeasurementRequired())
                .floorMeasurementRequired(roomType.getFloorMeasurementRequired())
                .active(roomType.getActive())
                .workItems(workItemSummaries)
                .createdAt(roomType.getCreatedAt())
                .updatedAt(roomType.getUpdatedAt())
                .createdBy(roomType.getCreatedBy())
                .updatedBy(roomType.getUpdatedBy())
                .build();
    }
}
