package com.fitouts.variation.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationBoqChangeRepository extends JpaRepository<VariationBoqChange, UUID> {
    List<VariationBoqChange> findByVariationUuidOrderByCreatedAtAsc(UUID variationUuid);
}
