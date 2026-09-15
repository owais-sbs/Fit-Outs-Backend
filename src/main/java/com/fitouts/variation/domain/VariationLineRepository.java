package com.fitouts.variation.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationLineRepository extends JpaRepository<VariationLine, UUID> {
    List<VariationLine> findByVariationUuidOrderBySortOrderAsc(UUID variationUuid);

    void deleteByVariationUuid(UUID variationUuid);
}
