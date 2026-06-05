package com.fitouts.lead.application;

import com.fitouts.lead.domain.LeadStatusHistory;
import com.fitouts.lead.domain.LeadStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeadStatusHistoryService {

    private final LeadStatusHistoryRepository repository;

    public LeadStatusHistoryService(
            LeadStatusHistoryRepository repository
    ) {
        this.repository = repository;
    }

    // GET HISTORY BY LEAD ID
    public List<LeadStatusHistory> getByLeadId(Long leadId) {

        return repository.findByLeadIdOrderByCreatedAtDesc(
                leadId
        );
    }
}