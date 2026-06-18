package com.fitouts.workitemconfiguration.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
public class WorkItemMasterService {

    private final WorkItemMasterRepository workItemMasterRepository;
    private final CompanyService companyService;

    public WorkItemMasterResponse create(WorkItemMasterCreateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        String code = request.getCode().trim().toUpperCase();
        // if (workItemMasterRepository.existsByCompanyUuidAndCodeAndDeletedFalse(companyId, code)) {
        //     throw new ConflictException("Work item master with code '" + code + "' already exists");
        // }

        WorkItemMaster workItemMaster = WorkItemMaster.builder()
                .company(companyService.getCompany(companyId))
                .name(request.getName().trim())
                .code(code)
                .build();

        WorkItemMaster saved = workItemMasterRepository.save(workItemMaster);
        return mapToResponse(saved);
    }

    public WorkItemMasterResponse update(UUID id, WorkItemMasterUpdateRequest request) {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }

        WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item master not found"));

        // if (!workItemMaster.getCompany().getUuid().equals(companyId)) {
        //     throw new BadRequestException("Work item master does not belong to current company");
        // }

        if (request.getCode() != null) {
            String code = request.getCode().trim().toUpperCase();
            // if (workItemMasterRepository.existsByCompanyUuidAndCodeAndIdNotAndDeletedFalse(companyId, code, id)) {
            //     throw new ConflictException("Work item master with code '" + code + "' already exists");
            // }
            workItemMaster.setCode(code);
        }

        if (request.getName() != null) {
            workItemMaster.setName(request.getName().trim());
        }

        WorkItemMaster updated = workItemMasterRepository.save(workItemMaster);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public WorkItemMasterResponse getById(UUID id) {
        WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item master not found"));
        return mapToResponse(workItemMaster);
    }

    @Transactional(readOnly = true)
    public List<WorkItemMasterResponse> list() {
        UUID companyId = CompanyContext.get();
        // if (companyId == null) {
        //     throw new BadRequestException("Company context is required");
        // }
        return workItemMasterRepository.findByCompanyUuidAndDeletedFalse(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public WorkItemMasterResponse activate(UUID id) {
        WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item master not found"));
        workItemMaster.setActive(true);
        return mapToResponse(workItemMasterRepository.save(workItemMaster));
    }

    public WorkItemMasterResponse deactivate(UUID id) {
        WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item master not found"));
        workItemMaster.setActive(false);
        return mapToResponse(workItemMasterRepository.save(workItemMaster));
    }

    public void softDelete(UUID id) {
        WorkItemMaster workItemMaster = workItemMasterRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Work item master not found"));
        workItemMaster.setDeleted(true);
        workItemMaster.setActive(false);
        workItemMasterRepository.save(workItemMaster);
    }

    private WorkItemMasterResponse mapToResponse(WorkItemMaster workItemMaster) {
        return WorkItemMasterResponse.builder()
                .id(workItemMaster.getId())
                .companyId(workItemMaster.getCompany() != null ? workItemMaster.getCompany().getUuid() : null)
                .name(workItemMaster.getName())
                .code(workItemMaster.getCode())
                .active(workItemMaster.getActive())
                .createdAt(workItemMaster.getCreatedAt())
                .updatedAt(workItemMaster.getUpdatedAt())
                .build();
    }
}
