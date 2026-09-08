package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageAwardRepository extends JpaRepository<PackageAward, UUID> {

    Optional<PackageAward> findByPackageId(UUID packageId);

    List<PackageAward> findBySubcontractorId(UUID subcontractorId);
}
