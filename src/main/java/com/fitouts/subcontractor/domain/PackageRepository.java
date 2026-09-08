package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageRepository extends JpaRepository<Package, UUID> {

    List<Package> findByCompanyId(UUID companyId);

    List<Package> findByCompanyIdAndProjectId(UUID companyId, Long projectId);

    Optional<Package> findByIdAndCompanyId(UUID id, UUID companyId);
}
