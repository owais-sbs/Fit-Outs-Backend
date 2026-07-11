package com.fitouts.procurement.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, UUID> {
    Optional<MaterialCategory> findByIdAndDeletedFalse(UUID id);
    List<MaterialCategory> findByCompanyUuidAndDeletedFalse(UUID companyUuid);
}
