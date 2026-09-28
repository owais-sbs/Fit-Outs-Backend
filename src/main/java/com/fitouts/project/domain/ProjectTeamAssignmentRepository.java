package com.fitouts.project.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectTeamAssignmentRepository extends JpaRepository<ProjectTeamAssignment, UUID> {

    List<ProjectTeamAssignment> findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(
            Long projectId, UUID companyId);

    List<ProjectTeamAssignment> findByProjectIdOrderByRoleAscDisplayNameAsc(Long projectId);

    List<ProjectTeamAssignment> findByAccountIdAndCompanyId(Long accountId, UUID companyId);

    List<ProjectTeamAssignment> findByAccountIdAndCompanyIdAndRole(
            Long accountId, UUID companyId, ProjectTeamRole role);

    boolean existsByProjectIdAndAccountIdAndCompanyIdAndRole(
            Long projectId, Long accountId, UUID companyId, ProjectTeamRole role);

    /**
     * Clears all team rows for a project. Scoped by projectId only because
     * {@code uq_project_team_assignment} is on (project_id, role, account_id).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProjectTeamAssignment a where a.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    Optional<ProjectTeamAssignment> findByUuidAndCompanyId(UUID uuid, UUID companyId);
}
