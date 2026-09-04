package com.fitouts.project.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTeamAssignmentRepository extends JpaRepository<ProjectTeamAssignment, UUID> {

    List<ProjectTeamAssignment> findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(
            Long projectId, UUID companyId);

    void deleteByProjectIdAndCompanyId(Long projectId, UUID companyId);

    Optional<ProjectTeamAssignment> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
