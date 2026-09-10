package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScQuoteLineRepository extends JpaRepository<ScQuoteLine, UUID> {

    List<ScQuoteLine> findByQuoteUuidOrderByBoqLineIdAsc(UUID quoteUuid);

    void deleteByQuoteUuid(UUID quoteUuid);
}
