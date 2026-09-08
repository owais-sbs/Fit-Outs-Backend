package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectPermitCaseRepository extends JpaRepository<ProjectPermitCase, UUID> {

    List<ProjectPermitCase> findByProjectIdOrderBySortOrderAsc(Long projectId);

    Optional<ProjectPermitCase> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByProjectId(Long projectId);
}
