package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScQuoteRepository extends JpaRepository<ScQuote, UUID> {

    List<ScQuote> findByPackageUuidOrderByVersionDesc(UUID packageUuid);

    List<ScQuote> findByPackageUuidAndOrganizationUuidOrderByVersionDesc(
            UUID packageUuid, UUID organizationUuid);

    List<ScQuote> findByOrganizationUuidOrderByUpdatedAtDesc(UUID organizationUuid);

    List<ScQuote> findByPackageUuidAndStatus(UUID packageUuid, ScQuoteStatus status);
}
