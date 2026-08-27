package com.fitouts.resource.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityCrewAssignmentRepository extends JpaRepository<ActivityCrewAssignment, UUID> {
    List<ActivityCrewAssignment> findByProjectIdAndCompanyIdOrderByStartDateAsc(Long projectId, UUID companyId);

    Optional<ActivityCrewAssignment> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    @Query("""
            SELECT a FROM ActivityCrewAssignment a
            WHERE a.crewUuid = :crewUuid
              AND a.companyId = :companyId
              AND a.startDate <= :endDate
              AND a.endDate >= :startDate
            """)
    List<ActivityCrewAssignment> findOverlapping(
            @Param("crewUuid") UUID crewUuid,
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    long countByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
