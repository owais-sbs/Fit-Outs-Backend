package com.fitouts.procurement.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.shared.enums.StockMovementType;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.material mat WHERE m.company.uuid = :companyId ORDER BY m.movementDate DESC")
    Page<StockMovement> findByCompany(UUID companyId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(m.totalCost), 0) FROM StockMovement m
            WHERE m.company.uuid = :companyId
              AND m.project.id = :projectId
              AND m.movementType = :movementType
            """)
    BigDecimal sumTotalCostByProjectAndType(
            @Param("companyId") UUID companyId,
            @Param("projectId") Long projectId,
            @Param("movementType") StockMovementType movementType);
}
