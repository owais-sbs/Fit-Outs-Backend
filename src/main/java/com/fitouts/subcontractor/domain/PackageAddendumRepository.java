package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageAddendumRepository extends JpaRepository<PackageAddendum, UUID> {

    List<PackageAddendum> findByPackageIdOrderByAddendumNumberAsc(UUID packageId);
}
