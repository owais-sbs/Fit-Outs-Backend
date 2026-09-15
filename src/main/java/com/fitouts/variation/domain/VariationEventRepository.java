package com.fitouts.variation.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationEventRepository extends JpaRepository<VariationEvent, UUID> {
    List<VariationEvent> findByVariationUuidOrderByCreatedAtAsc(UUID variationUuid);
}
