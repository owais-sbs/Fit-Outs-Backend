package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcontractorRepository extends JpaRepository<Subcontractor, UUID> {

    List<Subcontractor> findByCompanyId(UUID companyId);

    Optional<Subcontractor> findByLicenceNo(String licenceNo);

    Optional<Subcontractor> findByTrn(String trn);
}
