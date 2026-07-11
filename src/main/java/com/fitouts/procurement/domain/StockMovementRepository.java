package com.fitouts.procurement.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("SELECT m FROM StockMovement m JOIN FETCH m.material mat WHERE m.company.uuid = :companyId ORDER BY m.movementDate DESC")
    Page<StockMovement> findByCompany(UUID companyId, Pageable pageable);
}
