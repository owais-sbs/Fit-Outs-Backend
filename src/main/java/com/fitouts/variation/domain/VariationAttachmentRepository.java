package com.fitouts.variation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationAttachmentRepository extends JpaRepository<VariationAttachment, UUID> {
    List<VariationAttachment> findByVariationUuidOrderByCreatedAtAsc(UUID variationUuid);
    Optional<VariationAttachment> findByUuidAndVariationUuid(UUID uuid, UUID variationUuid);
}
