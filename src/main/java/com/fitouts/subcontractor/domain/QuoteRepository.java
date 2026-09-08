package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    List<Quote> findByPackageId(UUID packageId);

    Optional<Quote> findByPackageIdAndSubcontractorId(UUID packageId, UUID subcontractorId);

    List<Quote> findBySubcontractorId(UUID subcontractorId);
}
