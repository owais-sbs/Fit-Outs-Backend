package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageAddendumAckRepository extends JpaRepository<PackageAddendumAck, UUID> {

    List<PackageAddendumAck> findByPackageId(UUID packageId);

    List<PackageAddendumAck> findByPackageIdAndSubcontractorId(UUID packageId, UUID subcontractorId);

    Optional<PackageAddendumAck> findByAddendumIdAndSubcontractorId(UUID addendumId, UUID subcontractorId);
}
