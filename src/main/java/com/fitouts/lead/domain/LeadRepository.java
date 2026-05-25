package com.fitouts.lead.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, Long>,
        JpaSpecificationExecutor<Lead> {

}