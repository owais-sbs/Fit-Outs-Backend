package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseChecklistItemRepository extends JpaRepository<CaseChecklistItem, UUID> {

    List<CaseChecklistItem> findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(UUID caseUuid);

    Optional<CaseChecklistItem> findByUuidAndCaseUuid(UUID uuid, UUID caseUuid);

    List<CaseChecklistItem> findByDocumentTypeCode(String documentTypeCode);

    void deleteByCaseUuid(UUID caseUuid);
}
