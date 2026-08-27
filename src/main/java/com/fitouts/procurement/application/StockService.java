package com.fitouts.procurement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.company.application.CompanyService;
import com.fitouts.company.domain.Company;
import com.fitouts.procurement.api.*;
import com.fitouts.procurement.domain.*;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.StockMovementType;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final MaterialStockRepository materialStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MaterialService materialService;
    private final CompanyService companyService;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> listBalances() {
        UUID companyId = CompanyContext.get();
        return materialStockRepository.findAllByCompanyWithMaterial(companyId).stream()
                .map(this::mapBalance)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(int page, int size) {
        UUID companyId = CompanyContext.get();
        Pageable pageable = PageRequest.of(page, size);
        return stockMovementRepository.findByCompany(companyId, pageable).map(this::mapMovement);
    }

    public StockMovementResponse receipt(StockReceiptRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Receipt quantity must be positive");
        }
        Material material = materialService.find(request.getMaterialId());
        BigDecimal unitCost = request.getUnitCost() != null ? request.getUnitCost() : material.getCostPrice();
        BigDecimal total = unitCost != null
                ? unitCost.multiply(request.getQuantity()).setScale(2, RoundingMode.HALF_UP)
                : null;

        MaterialStock stock = getOrCreateStock(material);
        stock.setQuantityOnHand(stock.getQuantityOnHand().add(request.getQuantity()));
        materialStockRepository.save(stock);

        StockMovement movement = saveMovement(material, StockMovementType.RECEIPT, request.getQuantity(),
                unitCost, total, null, request.getReferenceNo(), request.getNotes(), request.getMovementDate());
        return mapMovement(movement);
    }

    public StockMovementResponse issue(StockIssueRequest request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Issue quantity must be positive");
        }
        Material material = materialService.find(request.getMaterialId());
        MaterialStock stock = getOrCreateStock(material);
        if (stock.availableQuantity().compareTo(request.getQuantity()) < 0) {
            throw new BadRequestException("Insufficient available stock for " + material.getMaterialName()
                    + " (on hand minus reserved)");
        }
        stock.setQuantityOnHand(stock.getQuantityOnHand().subtract(request.getQuantity()));
        materialStockRepository.save(stock);

        Project project = null;
        if (request.getProjectId() != null) {
            project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new NotFoundException("Project not found"));
        }

        BigDecimal unitCost = material.getCostPrice();
        BigDecimal total = unitCost != null
                ? unitCost.multiply(request.getQuantity()).setScale(2, RoundingMode.HALF_UP)
                : null;

        StockMovement movement = saveMovement(material, StockMovementType.ISSUE, request.getQuantity(),
                unitCost, total, project, request.getReferenceNo(), request.getNotes(), request.getMovementDate());
        return mapMovement(movement);
    }

    public StockMovementResponse adjust(StockAdjustmentRequest request) {
        if (request.getQuantity() == null) {
            throw new BadRequestException("Adjustment quantity is required");
        }
        Material material = materialService.find(request.getMaterialId());
        MaterialStock stock = getOrCreateStock(material);
        BigDecimal delta = request.getQuantity();
        BigDecimal newQty = stock.getQuantityOnHand().add(delta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Adjustment would result in negative stock");
        }
        stock.setQuantityOnHand(newQty);
        materialStockRepository.save(stock);

        StockMovement movement = saveMovement(material, StockMovementType.ADJUSTMENT, delta.abs(),
                material.getCostPrice(), null, null, null, request.getNotes(), null);
        return mapMovement(movement);
    }

    private MaterialStock getOrCreateStock(Material material) {
        UUID companyId = CompanyContext.get();
        return materialStockRepository.findByCompanyUuidAndMaterialId(companyId, material.getId())
                .orElseGet(() -> materialStockRepository.save(MaterialStock.builder()
                        .company(companyService.getCompany(companyId))
                        .material(material)
                        .quantityOnHand(BigDecimal.ZERO)
                        .quantityReserved(BigDecimal.ZERO)
                        .build()));
    }

    /**
     * Soft-hold: increase reserved quantity for a material (does not reduce on-hand).
     */
    public void increaseReserved(UUID materialId, BigDecimal delta) {
        if (materialId == null || delta == null || delta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Material material = materialService.find(materialId);
        MaterialStock stock = getOrCreateStock(material);
        BigDecimal current = stock.getQuantityReserved() != null ? stock.getQuantityReserved() : BigDecimal.ZERO;
        stock.setQuantityReserved(current.add(delta));
        materialStockRepository.save(stock);
    }

    private StockMovement saveMovement(Material material, StockMovementType type, BigDecimal quantity,
            BigDecimal unitCost, BigDecimal totalCost, Project project,
            String referenceNo, String notes, LocalDateTime movementDate) {
        Company company = companyService.getCompany(CompanyContext.get());
        StockMovement movement = StockMovement.builder()
                .company(company)
                .material(material)
                .movementType(type)
                .quantity(quantity)
                .unitCost(unitCost)
                .totalCost(totalCost)
                .project(project)
                .referenceNo(referenceNo)
                .notes(notes)
                .movementDate(movementDate != null ? movementDate : LocalDateTime.now())
                .build();
        return stockMovementRepository.save(movement);
    }

    private StockBalanceResponse mapBalance(MaterialStock stock) {
        Material m = stock.getMaterial();
        BigDecimal cost = m.getCostPrice() != null ? m.getCostPrice() : BigDecimal.ZERO;
        BigDecimal value = stock.getQuantityOnHand().multiply(cost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal min = m.getMinStockLevel() != null ? m.getMinStockLevel() : BigDecimal.ZERO;
        BigDecimal reserved = stock.getQuantityReserved() != null ? stock.getQuantityReserved() : BigDecimal.ZERO;
        BigDecimal available = stock.availableQuantity();
        return StockBalanceResponse.builder()
                .materialId(m.getId())
                .materialName(m.getMaterialName())
                .materialCode(m.getMaterialCode())
                .materialCategoryName(m.getMaterialCategory() != null ? m.getMaterialCategory().getName() : null)
                .quantityOnHand(stock.getQuantityOnHand())
                .quantityReserved(reserved)
                .quantityAvailable(available)
                .costPrice(m.getCostPrice())
                .stockValue(value)
                .minStockLevel(min)
                .lowStock(available.compareTo(min) < 0)
                .lastUpdated(stock.getUpdatedAt())
                .build();
    }

    private StockMovementResponse mapMovement(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .materialId(movement.getMaterial().getId())
                .materialName(movement.getMaterial().getMaterialName())
                .materialCode(movement.getMaterial().getMaterialCode())
                .movementType(movement.getMovementType())
                .quantity(movement.getQuantity())
                .unitCost(movement.getUnitCost())
                .totalCost(movement.getTotalCost())
                .projectId(movement.getProject() != null ? movement.getProject().getId() : null)
                .projectName(movement.getProject() != null ? movement.getProject().getName() : null)
                .referenceNo(movement.getReferenceNo())
                .notes(movement.getNotes())
                .movementDate(movement.getMovementDate())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
