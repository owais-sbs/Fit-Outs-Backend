package com.fitouts.lead.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {

    List<LeadSource> findByIsdeletedFalseAndIsactiveTrue();

}