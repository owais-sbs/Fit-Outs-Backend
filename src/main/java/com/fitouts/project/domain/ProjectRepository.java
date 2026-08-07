package com.fitouts.project.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    List<Project> findByCompanyIdAndIsDeletedFalse(UUID companyId);

    List<Project> findByCompanyIdAndClientIdAndIsDeletedFalse(UUID companyId, Long clientId);
}
