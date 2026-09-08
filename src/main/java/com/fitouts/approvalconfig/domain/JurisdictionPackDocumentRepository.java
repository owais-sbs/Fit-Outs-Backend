package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface JurisdictionPackDocumentRepository extends JpaRepository<JurisdictionPackDocument, UUID> {

    List<JurisdictionPackDocument> findByPackPermitId(UUID packPermitId);

    List<JurisdictionPackDocument> findByPackPermitIdIn(List<UUID> packPermitIds);

    @Modifying
    @Transactional
    void deleteByPackPermitId(UUID packPermitId);

    @Modifying
    @Transactional
    void deleteByPackPermitIdIn(List<UUID> packPermitIds);
}
