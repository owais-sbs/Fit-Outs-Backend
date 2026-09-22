package com.fitouts.variation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCommercialRepository extends JpaRepository<ProjectCommercial, UUID> {
    Optional<ProjectCommercial> findByProjectIdAndCompanyId(Long projectId, UUID companyId);

    List<ProjectCommercial> findByCompanyId(UUID companyId);
}
