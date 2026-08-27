package com.fitouts.appendix.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.appendix.domain.SiteVisitEstimateAppendix;
import com.fitouts.appendix.domain.SiteVisitEstimateAppendixId;

public interface SiteVisitEstimateAppendixRepository
        extends JpaRepository<SiteVisitEstimateAppendix, SiteVisitEstimateAppendixId> {

    List<SiteVisitEstimateAppendix> findByEstimateUuidOrderByDisplayOrderAsc(UUID estimateUuid);

    void deleteByEstimateUuid(UUID estimateUuid);
}
