package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScComplianceDocumentRepository extends JpaRepository<ScComplianceDocument, UUID> {

    List<ScComplianceDocument> findByProfileUuidOrderByDocumentTypeAsc(UUID profileUuid);

    Optional<ScComplianceDocument> findByProfileUuidAndDocumentType(UUID profileUuid, ScComplianceDocType documentType);
}
