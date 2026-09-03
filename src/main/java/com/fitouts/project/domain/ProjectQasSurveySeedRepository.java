package com.fitouts.project.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectQasSurveySeedRepository extends JpaRepository<ProjectQasSurveySeed, UUID> {

    Optional<ProjectQasSurveySeed> findByProjectIdAndCompanyId(Long projectId, UUID companyId);

    void deleteByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
