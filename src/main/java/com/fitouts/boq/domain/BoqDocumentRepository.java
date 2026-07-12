package com.fitouts.boq.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.shared.enums.BoqDocumentStatus;

public interface BoqDocumentRepository extends JpaRepository<BoqDocument, UUID> {
    List<BoqDocument> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<BoqDocument> findByIdAndCompanyId(UUID id, UUID companyId);
    List<BoqDocument> findByCompanyIdAndStatusInOrderBySubmittedAtDesc(UUID companyId, List<BoqDocumentStatus> statuses);
    List<BoqDocument> findByParentBoqIdOrderByCreatedAtAsc(UUID parentBoqId);
}
