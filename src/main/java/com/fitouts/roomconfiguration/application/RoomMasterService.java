package com.fitouts.roomconfiguration.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
public class RoomMasterService {

    private final RoomMasterRepository roomMasterRepository;
    private final CompanyService companyService;

    public RoomMasterResponse create(RoomMasterCreateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        String code = request.getCode().trim().toUpperCase();
        // if (roomMasterRepository.existsByCompanyUuidAndCodeAndDeletedFalse(companyId, code)) {
        //     throw new ConflictException("Room master with code '" + code + "' already exists");
        // }

        RoomMaster roomMaster = RoomMaster.builder()
                .company(companyService.getCompany(companyId))
                .name(request.getName().trim())
                .code(code)
                .build();

        RoomMaster saved = roomMasterRepository.save(roomMaster);
        return mapToResponse(saved);
    }

    public RoomMasterResponse update(UUID id, RoomMasterUpdateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room master not found"));

        // if (!roomMaster.getCompany().getUuid().equals(companyId)) {
        //     throw new BadRequestException("Room master does not belong to current company");
        // }

        if (request.getCode() != null) {
            String code = request.getCode().trim().toUpperCase();
            // if (roomMasterRepository.existsByCompanyUuidAndCodeAndIdNotAndDeletedFalse(companyId, code, id)) {
            //     throw new ConflictException("Room master with code '" + code + "' already exists");
            // }
            roomMaster.setCode(code);
        }

        if (request.getName() != null) {
            roomMaster.setName(request.getName().trim());
        }

        RoomMaster updated = roomMasterRepository.save(roomMaster);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public RoomMasterResponse getById(UUID id) {
        RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room master not found"));
        return mapToResponse(roomMaster);
    }

    @Transactional(readOnly = true)
    public List<RoomMasterResponse> list() {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }
        return roomMasterRepository.findByCompanyUuidAndDeletedFalse(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public RoomMasterResponse activate(UUID id) {
        RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room master not found"));
        roomMaster.setActive(true);
        return mapToResponse(roomMasterRepository.save(roomMaster));
    }

    public RoomMasterResponse deactivate(UUID id) {
        RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room master not found"));
        roomMaster.setActive(false);
        return mapToResponse(roomMasterRepository.save(roomMaster));
    }

    public void softDelete(UUID id) {
        RoomMaster roomMaster = roomMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Room master not found"));
        roomMaster.setDeleted(true);
        roomMaster.setActive(false);
        roomMasterRepository.save(roomMaster);
    }

    private RoomMasterResponse mapToResponse(RoomMaster roomMaster) {
        return RoomMasterResponse.builder()
                .id(roomMaster.getId())
                .companyId(roomMaster.getCompany() != null ? roomMaster.getCompany().getUuid() : null)
                .name(roomMaster.getName())
                .code(roomMaster.getCode())
                .active(roomMaster.getActive())
                .createdAt(roomMaster.getCreatedAt())
                .updatedAt(roomMaster.getUpdatedAt())
                .build();
    }
}
