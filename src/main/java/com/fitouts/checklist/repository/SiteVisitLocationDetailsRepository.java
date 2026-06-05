package com.fitouts.checklist.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.checklist.domain.SiteVisitLocationDetails;

public interface SiteVisitLocationDetailsRepository extends JpaRepository<SiteVisitLocationDetails, UUID> {

    boolean existsBySiteVisitUuid(UUID siteVisitUuid);
}
