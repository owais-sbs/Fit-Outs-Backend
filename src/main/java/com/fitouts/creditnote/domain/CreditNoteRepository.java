package com.fitouts.creditnote.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<CreditNote> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    long countByProjectIdAndCompanyId(Long projectId, UUID companyId);
}
