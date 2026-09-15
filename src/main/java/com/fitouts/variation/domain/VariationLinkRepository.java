package com.fitouts.variation.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationLinkRepository extends JpaRepository<VariationLink, UUID> {
    List<VariationLink> findByVariationUuid(UUID variationUuid);

    void deleteByVariationUuid(UUID variationUuid);
}
