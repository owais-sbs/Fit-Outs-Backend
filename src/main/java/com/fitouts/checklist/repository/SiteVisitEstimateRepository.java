package com.fitouts.checklist.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.SiteVisitEstimate;

public interface SiteVisitEstimateRepository extends JpaRepository<SiteVisitEstimate, UUID> {

    Optional<SiteVisitEstimate> findBySiteVisitUuid(UUID siteVisitUuid);

    boolean existsBySiteVisitUuid(UUID siteVisitUuid);
}
