package com.fitouts.procurement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.company.domain.Company;
import com.fitouts.procurement.api.*;
import com.fitouts.procurement.domain.*;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialCategoryService materialCategoryService;
    private final MaterialStockRepository materialStockRepository;
    private final CompanyService companyService;

    public MaterialResponse create(MaterialCreateRequest request) {
        UUID companyId = CompanyContext.get();
        Company company = companyService.getCompany(companyId);

        MaterialCategory category = null;
        if (request.getMaterialCategoryId() != null) {
            category = materialCategoryService.find(request.getMaterialCategoryId());
        }

        Material material = Material.builder()
                .company(company)
                .materialCategory(category)
                .materialName(request.getMaterialName().trim())
                .materialCode(request.getMaterialCode().trim().toUpperCase())
                .unitType(request.getUnitType())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .supplierName(request.getSupplierName())
                .sku(request.getSku())
                .minStockLevel(defaultDecimal(request.getMinStockLevel()))
                .reorderQty(defaultDecimal(request.getReorderQty()))
                .description(request.getDescription())
                .build();

        Material saved = materialRepository.save(material);
        materialStockRepository.save(MaterialStock.builder()
                .company(company)
                .material(saved)
                .quantityOnHand(BigDecimal.ZERO)
                .build());

        return mapToResponse(saved, BigDecimal.ZERO);
    }

    public MaterialResponse update(UUID id, MaterialUpdateRequest request) {
        Material material = find(id);
        if (request.getMaterialName() != null) material.setMaterialName(request.getMaterialName().trim());
        if (request.getMaterialCode() != null) material.setMaterialCode(request.getMaterialCode().trim().toUpperCase());
        if (request.getMaterialCategoryId() != null) {
            material.setMaterialCategory(materialCategoryService.find(request.getMaterialCategoryId()));
        }
        if (request.getUnitType() != null) material.setUnitType(request.getUnitType());
        if (request.getCostPrice() != null) material.setCostPrice(request.getCostPrice());
        if (request.getSellingPrice() != null) material.setSellingPrice(request.getSellingPrice());
        if (request.getSupplierName() != null) material.setSupplierName(request.getSupplierName());
        if (request.getSku() != null) material.setSku(request.getSku());
        if (request.getMinStockLevel() != null) material.setMinStockLevel(request.getMinStockLevel());
        if (request.getReorderQty() != null) material.setReorderQty(request.getReorderQty());
        if (request.getDescription() != null) material.setDescription(request.getDescription());

        Material updated = materialRepository.save(material);
        BigDecimal qty = getQuantityOnHand(updated.getId());
        return mapToResponse(updated, qty);
    }

    @Transactional(readOnly = true)
    public MaterialResponse getById(UUID id) {
        Material material = find(id);
        return mapToResponse(material, getQuantityOnHand(material.getId()));
    }

    @Transactional(readOnly = true)
    public Page<MaterialResponse> list(MaterialFilterRequest filter, int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size, Sort.by("materialName").ascending());
        Specification<Material> spec = MaterialSpecification.filter(filter, companyId);
        Page<Material> materials = materialRepository.findAll(spec, pageable);
        List<UUID> pageIds = materials.getContent().stream().map(Material::getId).toList();
        Map<UUID, BigDecimal> stockMap = pageIds.isEmpty()
                ? Map.of()
                : materialStockRepository.findByCompanyUuidAndMaterialIdIn(companyId, pageIds).stream()
                        .collect(Collectors.toMap(
                                s -> s.getMaterial().getId(),
                                MaterialStock::getQuantityOnHand,
                                (a, b) -> a));

        return materials.map(m -> mapToResponse(m, stockMap.getOrDefault(m.getId(), BigDecimal.ZERO)));
    }

    public MaterialResponse activate(UUID id) {
        Material material = find(id);
        material.setActive(true);
        return mapToResponse(materialRepository.save(material), getQuantityOnHand(id));
    }

    public MaterialResponse deactivate(UUID id) {
        Material material = find(id);
        material.setActive(false);
        return mapToResponse(materialRepository.save(material), getQuantityOnHand(id));
    }

    public void softDelete(UUID id) {
        Material material = find(id);
        material.setDeleted(true);
        material.setActive(false);
        materialRepository.save(material);
    }

    Material find(UUID id) {
        return materialRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Material not found"));
    }

    private BigDecimal getQuantityOnHand(UUID materialId) {
        UUID companyId = CompanyContext.get();
        return materialStockRepository.findByCompanyUuidAndMaterialId(companyId, materialId)
                .map(MaterialStock::getQuantityOnHand)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    MaterialResponse mapToResponse(Material material, BigDecimal quantityOnHand) {
        BigDecimal qty = quantityOnHand != null ? quantityOnHand : BigDecimal.ZERO;
        BigDecimal min = material.getMinStockLevel() != null ? material.getMinStockLevel() : BigDecimal.ZERO;
        boolean lowStock = qty.compareTo(min) < 0;
        return MaterialResponse.builder()
                .id(material.getId())
                .companyId(material.getCompany() != null ? material.getCompany().getUuid() : null)
                .materialCategoryId(material.getMaterialCategory() != null ? material.getMaterialCategory().getId() : null)
                .materialCategoryName(material.getMaterialCategory() != null ? material.getMaterialCategory().getName() : null)
                .materialName(material.getMaterialName())
                .materialCode(material.getMaterialCode())
                .unitType(material.getUnitType())
                .costPrice(material.getCostPrice())
                .sellingPrice(material.getSellingPrice())
                .supplierName(material.getSupplierName())
                .sku(material.getSku())
                .minStockLevel(material.getMinStockLevel())
                .reorderQty(material.getReorderQty())
                .description(material.getDescription())
                .active(material.getActive())
                .quantityOnHand(qty)
                .lowStock(lowStock)
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
