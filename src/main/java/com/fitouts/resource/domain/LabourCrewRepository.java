package com.fitouts.resource.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LabourCrewRepository extends JpaRepository<LabourCrew, UUID> {
    List<LabourCrew> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<LabourCrew> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
