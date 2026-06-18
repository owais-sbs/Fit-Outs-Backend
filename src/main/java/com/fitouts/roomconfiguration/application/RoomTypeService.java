package com.fitouts.roomconfiguration.application;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final CompanyService companyService;
    private final RoomMasterRepository roomMasterRepository;

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

        RoomType saved = roomTypeRepository.save(roomType);
        return mapToResponse(saved);
    }

    public RoomTypeResponse update(UUID id, RoomTypeUpdateRequest request) {
        UUID companyId = CompanyContext.get();

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
                .build();
    }
}
