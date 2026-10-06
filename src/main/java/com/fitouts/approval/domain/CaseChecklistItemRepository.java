package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseChecklistItemRepository extends JpaRepository<CaseChecklistItem, UUID> {

    List<CaseChecklistItem> findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(UUID caseUuid);

    Optional<CaseChecklistItem> findByUuidAndCaseUuid(UUID uuid, UUID caseUuid);

    List<CaseChecklistItem> findByDocumentTypeCode(String documentTypeCode);

    @Query("SELECT i FROM CaseChecklistItem i, ApprovalCase c "
            + "WHERE i.caseUuid = c.uuid AND c.projectId = :projectId AND c.companyId = :companyId "
            + "AND i.documentTypeCode = :documentTypeCode")
    List<CaseChecklistItem> findByProjectCompanyAndDocumentTypeCode(
            @Param("projectId") Long projectId,
            @Param("companyId") UUID companyId,
            @Param("documentTypeCode") String documentTypeCode);

    @Query("SELECT i FROM CaseChecklistItem i, ApprovalCase c "
            + "WHERE i.caseUuid = c.uuid AND c.companyId = :companyId "
            + "AND upper(i.documentTypeCode) = upper(:documentTypeCode)")
    List<CaseChecklistItem> findByCompanyAndDocumentTypeCode(
            @Param("companyId") UUID companyId,
            @Param("documentTypeCode") String documentTypeCode);

    void deleteByCaseUuid(UUID caseUuid);
}
