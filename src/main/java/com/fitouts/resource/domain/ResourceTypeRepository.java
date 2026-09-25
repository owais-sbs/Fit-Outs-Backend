package com.fitouts.resource.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, UUID> {
    List<ResourceType> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<ResourceType> findByCompanyIdAndKindOrderByNameAsc(UUID companyId, ResourceKind kind);

    Optional<ResourceType> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
