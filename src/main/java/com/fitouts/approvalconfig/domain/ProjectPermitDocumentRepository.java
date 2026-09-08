package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectPermitDocumentRepository extends JpaRepository<ProjectPermitDocument, UUID> {

    List<ProjectPermitDocument> findByCaseId(UUID caseId);

    List<ProjectPermitDocument> findByCaseIdIn(List<UUID> caseIds);
}
