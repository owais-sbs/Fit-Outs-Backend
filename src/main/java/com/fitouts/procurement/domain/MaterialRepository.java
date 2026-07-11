package com.fitouts.procurement.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MaterialRepository extends JpaRepository<Material, UUID>, JpaSpecificationExecutor<Material> {
    Optional<Material> findByIdAndDeletedFalse(UUID id);
    boolean existsByCompanyUuidAndMaterialCodeAndDeletedFalse(UUID companyUuid, String materialCode);
    boolean existsByCompanyUuidAndMaterialCodeAndIdNotAndDeletedFalse(UUID companyUuid, String materialCode, UUID id);
}
