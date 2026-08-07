package com.fitouts.checklist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.SiteVisit;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, UUID> {

    @EntityGraph(attributePaths = { "assignments", "assignments.employee", "locationDetails" })
    List<SiteVisit> findByCompanyUuid(UUID companyUuid);

    List<SiteVisit> findByAssignedToId(Long employeeId);
}
