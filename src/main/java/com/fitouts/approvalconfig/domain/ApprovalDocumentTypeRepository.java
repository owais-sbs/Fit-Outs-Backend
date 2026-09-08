package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDocumentTypeRepository extends JpaRepository<ApprovalDocumentType, UUID> {

    List<ApprovalDocumentType> findByCompanyIdAndDeletedFalseOrderByDocCodeAsc(UUID companyId);

    Optional<ApprovalDocumentType> findByIdAndDeletedFalse(UUID id);

    Optional<ApprovalDocumentType> findByCompanyIdAndDocCodeAndDeletedFalse(UUID companyId, String docCode);
}
