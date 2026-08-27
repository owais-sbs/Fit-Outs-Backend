package com.fitouts.planning.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningGateConfigRepository extends JpaRepository<PlanningGateConfig, UUID> {

    Optional<PlanningGateConfig> findByCompanyId(UUID companyId);
}
