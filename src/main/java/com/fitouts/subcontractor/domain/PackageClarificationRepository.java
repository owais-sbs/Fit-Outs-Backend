package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageClarificationRepository extends JpaRepository<PackageClarification, UUID> {

    List<PackageClarification> findByPackageId(UUID packageId);
}
