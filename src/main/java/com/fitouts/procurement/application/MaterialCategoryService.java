package com.fitouts.procurement.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.procurement.api.*;
import com.fitouts.procurement.domain.*;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialCategoryService {

    private final MaterialCategoryRepository repository;
    private final CompanyService companyService;

    public MaterialCategoryResponse create(MaterialCategoryCreateRequest request) {
        UUID companyId = CompanyContext.get();
        MaterialCategory category = MaterialCategory.builder()
                .company(companyService.getCompany(companyId))
                .name(request.getName().trim())
                .code(request.getCode() != null ? request.getCode().trim().toUpperCase() : null)
                .build();
        return mapToResponse(repository.save(category));
    }

    public MaterialCategoryResponse update(UUID id, MaterialCategoryUpdateRequest request) {
        MaterialCategory category = find(id);
        if (request.getName() != null) category.setName(request.getName().trim());
        if (request.getCode() != null) category.setCode(request.getCode().trim().toUpperCase());
        return mapToResponse(repository.save(category));
    }

    @Transactional(readOnly = true)
    public MaterialCategoryResponse getById(UUID id) {
        return mapToResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<MaterialCategoryResponse> list() {
        UUID companyId = CompanyContext.get();
        return repository.findByCompanyUuidAndDeletedFalse(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public MaterialCategoryResponse activate(UUID id) {
        MaterialCategory category = find(id);
        category.setActive(true);
        return mapToResponse(repository.save(category));
    }

    public MaterialCategoryResponse deactivate(UUID id) {
        MaterialCategory category = find(id);
        category.setActive(false);
        return mapToResponse(repository.save(category));
    }

    public void softDelete(UUID id) {
        MaterialCategory category = find(id);
        category.setDeleted(true);
        category.setActive(false);
        repository.save(category);
    }

    MaterialCategory find(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Material category not found"));
    }

    private MaterialCategoryResponse mapToResponse(MaterialCategory category) {
        return MaterialCategoryResponse.builder()
                .id(category.getId())
                .companyId(category.getCompany() != null ? category.getCompany().getUuid() : null)
                .name(category.getName())
                .code(category.getCode())
                .active(category.getActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
