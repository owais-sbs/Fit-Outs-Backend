package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JurisdictionPackRepository extends JpaRepository<JurisdictionPack, UUID> {

    List<JurisdictionPack> findByCompanyIdAndDeletedFalseOrderByNameAsc(UUID companyId);

    List<JurisdictionPack> findByCompanyIdAndDeletedFalseAndSelectableTrueAndActiveTrueOrderByNameAsc(UUID companyId);

    Optional<JurisdictionPack> findByIdAndDeletedFalse(UUID id);

    Optional<JurisdictionPack> findByCompanyIdAndCodeAndDeletedFalse(UUID companyId, String code);

    boolean existsByCompanyIdAndDeletedFalse(UUID companyId);
}
