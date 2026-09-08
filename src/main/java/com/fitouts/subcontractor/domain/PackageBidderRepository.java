package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageBidderRepository extends JpaRepository<PackageBidder, UUID> {

    List<PackageBidder> findByPackageId(UUID packageId);

    List<PackageBidder> findBySubcontractorId(UUID subcontractorId);

    Optional<PackageBidder> findByPackageIdAndSubcontractorId(UUID packageId, UUID subcontractorId);
}
