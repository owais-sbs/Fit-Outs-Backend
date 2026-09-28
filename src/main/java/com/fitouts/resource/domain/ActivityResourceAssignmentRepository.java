package com.fitouts.resource.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityResourceAssignmentRepository extends JpaRepository<ActivityResourceAssignment, UUID> {
    List<ActivityResourceAssignment> findByProjectIdAndCompanyIdOrderByStartDateAsc(Long projectId, UUID companyId);

    Optional<ActivityResourceAssignment> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    @Query("""
            SELECT a FROM ActivityResourceAssignment a
            WHERE a.resourceTypeUuid = :resourceTypeUuid
              AND a.companyId = :companyId
              AND a.startDate <= :endDate
              AND a.endDate >= :startDate
            """)
    List<ActivityResourceAssignment> findOverlapping(
            @Param("resourceTypeUuid") UUID resourceTypeUuid,
            @Param("companyId") UUID companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    long countByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
