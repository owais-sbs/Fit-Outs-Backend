package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorDocumentRepository extends JpaRepository<SubcontractorDocument, UUID> {

    List<SubcontractorDocument> findBySubcontractorId(UUID subcontractorId);
}
