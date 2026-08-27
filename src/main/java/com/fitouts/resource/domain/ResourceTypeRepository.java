package com.fitouts.resource.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, UUID> {
    List<ResourceType> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<ResourceType> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
