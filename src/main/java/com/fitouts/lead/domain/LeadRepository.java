package com.fitouts.lead.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, Long>,
        JpaSpecificationExecutor<Lead> {

    List<Lead> findByCompanyEntityUuidAndIsdeletedFalse(UUID companyUuid);
}