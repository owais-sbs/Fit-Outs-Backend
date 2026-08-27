package com.fitouts.planning.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningDecisionAuditRepository extends JpaRepository<PlanningDecisionAudit, UUID> {

    List<PlanningDecisionAudit> findByProjectIdAndCompanyIdOrderByDecidedAtDesc(Long projectId, UUID companyId);
}
