package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPackageAwardRepository extends JpaRepository<ScPackageAward, UUID> {

    Optional<ScPackageAward> findByPackageUuid(UUID packageUuid);

    List<ScPackageAward> findByOrganizationUuid(UUID organizationUuid);
}
