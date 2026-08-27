package com.fitouts.checklist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.domain.SiteVisitEstimateStatus;

public interface SiteVisitEstimateRepository extends JpaRepository<SiteVisitEstimate, UUID> {

    Optional<SiteVisitEstimate> findBySiteVisitUuid(UUID siteVisitUuid);

    boolean existsBySiteVisitUuid(UUID siteVisitUuid);

    @Query("""
            SELECT e FROM SiteVisitEstimate e
            JOIN e.siteVisit sv
            WHERE e.status = :status
              AND sv.company.uuid = :companyId
              AND sv.leadId IN (
                  SELECT l.id FROM Lead l WHERE LOWER(l.email) = LOWER(:email)
              )
            ORDER BY e.updatedAt DESC
            """)
    List<SiteVisitEstimate> findIssuedForClientEmail(
            @Param("companyId") UUID companyId,
            @Param("email") String email,
            @Param("status") SiteVisitEstimateStatus status);
}
