package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScOrganizationDocumentRepository extends JpaRepository<ScOrganizationDocument, UUID> {

    List<ScOrganizationDocument> findByOrganizationUuidOrderByDocumentCodeAsc(UUID organizationUuid);

    Optional<ScOrganizationDocument> findByOrganizationUuidAndDocumentCode(
            UUID organizationUuid, ScOrgDocumentCode documentCode);
}
