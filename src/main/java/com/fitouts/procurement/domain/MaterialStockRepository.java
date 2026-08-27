package com.fitouts.procurement.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialStockRepository extends JpaRepository<MaterialStock, UUID> {
    Optional<MaterialStock> findByCompanyUuidAndMaterialId(UUID companyUuid, UUID materialId);

    @Query("SELECT s FROM MaterialStock s JOIN FETCH s.material m WHERE s.company.uuid = :companyId AND m.deleted = false")
    List<MaterialStock> findAllByCompanyWithMaterial(@Param("companyId") UUID companyId);

    @Query("""
            SELECT s FROM MaterialStock s JOIN FETCH s.material m
            WHERE s.company.uuid = :companyId AND m.id IN :materialIds AND m.deleted = false
            """)
    List<MaterialStock> findByCompanyUuidAndMaterialIdIn(
            @Param("companyId") UUID companyId,
            @Param("materialIds") Collection<UUID> materialIds);
}
