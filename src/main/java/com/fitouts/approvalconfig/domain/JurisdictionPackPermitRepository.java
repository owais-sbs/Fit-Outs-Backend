package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface JurisdictionPackPermitRepository extends JpaRepository<JurisdictionPackPermit, UUID> {

    List<JurisdictionPackPermit> findByPackIdOrderBySortOrderAsc(UUID packId);

    List<JurisdictionPackPermit> findByPackIdInOrderBySortOrderAsc(List<UUID> packIds);

    @Modifying
    @Transactional
    void deleteByPackId(UUID packId);
}
