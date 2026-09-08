package com.fitouts.approval.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyComplianceRepository extends JpaRepository<CompanyCompliance, UUID> {

    List<CompanyCompliance> findByCompanyIdOrderByDocumentTypeCodeAsc(UUID companyId);

    Optional<CompanyCompliance> findByCompanyIdAndDocumentTypeCode(UUID companyId, String documentTypeCode);

    List<CompanyCompliance> findByExpiryDateNotNullAndExpiryDateLessThanEqual(LocalDate cutoff);
}
