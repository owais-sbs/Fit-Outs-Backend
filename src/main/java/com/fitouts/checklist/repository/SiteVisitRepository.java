package com.fitouts.checklist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.checklist.domain.SiteVisit;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, UUID> {

    @EntityGraph(attributePaths = { "assignments", "assignments.employee", "locationDetails" })
    List<SiteVisit> findByCompanyUuid(UUID companyUuid);

    List<SiteVisit> findByAssignedToId(Long employeeId);

    @EntityGraph(attributePaths = { "assignments", "assignments.employee", "locationDetails" })
    @Query("""
            SELECT sv FROM SiteVisit sv
            WHERE sv.company.uuid = :companyId
              AND sv.leadId IN (
                  SELECT l.id FROM Lead l
                  WHERE LOWER(l.email) = LOWER(:email)
              )
            """)
    List<SiteVisit> findByCompanyUuidAndLeadEmail(
            @Param("companyId") UUID companyId,
            @Param("email") String email);

    @EntityGraph(attributePaths = { "assignments", "assignments.employee", "locationDetails" })
    @Query("""
            SELECT DISTINCT sv FROM SiteVisit sv
            LEFT JOIN sv.assignments a
            WHERE sv.company.uuid = :companyId
              AND (
                    (sv.assignedTo IS NOT NULL AND sv.assignedTo.id = :accountId)
                 OR (a.employee IS NOT NULL AND a.employee.id = :accountId)
              )
            """)
    List<SiteVisit> findByCompanyUuidAndAssigneeAccountId(
            @Param("companyId") UUID companyId,
            @Param("accountId") Long accountId);
}
