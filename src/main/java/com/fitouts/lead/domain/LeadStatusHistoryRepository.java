package com.fitouts.lead.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadStatusHistoryRepository
        extends JpaRepository<LeadStatusHistory, Long> {

    List<LeadStatusHistory> findByLeadIdOrderByCreatedAtDesc(Long leadId);

    List<LeadStatusHistory> findByLeadIdAndCompanyIdOrderByCreatedAtDesc(Long leadId, Long companyId);
}