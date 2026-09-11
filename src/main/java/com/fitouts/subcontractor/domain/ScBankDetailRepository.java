package com.fitouts.subcontractor.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScBankDetailRepository extends JpaRepository<ScBankDetail, UUID> {

    Optional<ScBankDetail> findByOrganizationUuid(UUID organizationUuid);
}
