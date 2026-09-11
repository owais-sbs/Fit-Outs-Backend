package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScInvoiceRepository extends JpaRepository<ScInvoice, UUID> {

    List<ScInvoice> findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(UUID packageUuid, UUID companyId);

    Optional<ScInvoice> findByUuidAndCompanyId(UUID uuid, UUID companyId);

    List<ScInvoice> findByCompanyIdAndStatusOrderBySubmittedAtDesc(UUID companyId, ScInvoiceStatus status);

    List<ScInvoice> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);
}
