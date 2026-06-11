package com.fitouts.lead.application;

import com.fitouts.company.application.CompanyService;
import com.fitouts.lead.domain.*;
import com.fitouts.shared.context.CompanyContext;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LeadService {

    private final LeadRepository leadRepository;

    private final LeadStatusHistoryRepository historyRepository;

    private final CompanyService companyService;

    public LeadService(LeadRepository leadRepository,
                       LeadStatusHistoryRepository historyRepository,
                       CompanyService companyService) {

        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.companyService = companyService;
    }

    // CREATE LEAD
    public Lead create(Lead request) {

        request.setId(null);

        request.setReferenceNo(generateReference());

        request.setStatus(LeadStatus.NEW);

        request.setIsactive(true);

        request.setIsdeleted(false);

        request.setCreatedAt(LocalDateTime.now());

        request.setUpdatedAt(LocalDateTime.now());

        request.setLastActivityDate(LocalDateTime.now());

        // Set company from context
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            request.setCompanyEntity(companyService.getCompany(companyId));
        }

        Lead saved = leadRepository.save(request);

        // CREATE STATUS HISTORY
        LeadStatusHistory history = new LeadStatusHistory();

        history.setLeadId(saved.getId());

        history.setStatus(LeadStatus.NEW);

        history.setNotes("Lead Created");

        history.setCreatedAt(LocalDateTime.now());

        historyRepository.save(history);

        // TODO:
        // Notify assignee

        return saved;
    }

    // UPDATE STATUS
    public Lead updateStatus(Long leadId,
                             LeadStatus status,
                             Long updatedBy,
                             String notes,
                             String lostReason) {

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (status == LeadStatus.LOST &&
                (lostReason == null || lostReason.isEmpty())) {

            throw new RuntimeException("Lost reason is required");
        }

        lead.setStatus(status);

        lead.setUpdatedAt(LocalDateTime.now());

        lead.setLastActivityDate(LocalDateTime.now());

        Lead updated = leadRepository.save(lead);

        LeadStatusHistory history = new LeadStatusHistory();

        history.setLeadId(leadId);

        history.setStatus(status);

        history.setUpdatedBy(updatedBy);

        history.setNotes(notes);

        history.setLostReason(lostReason);

        history.setCreatedAt(LocalDateTime.now());

        historyRepository.save(history);

        return updated;
    }

    // FILTERED PAGINATION — scoped to current company
    public Page<Lead> getAll(LeadFilterDTO filter,
                             int page,
                             int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        if (filter == null) filter = new LeadFilterDTO();
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            filter.setCompanyUuid(companyId);
        }

        return leadRepository.findAll(
                LeadSpecification.filterLeads(filter),
                pageable
        );
    }

    // GET BY ID
    public Lead getById(Long id) {

        return leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
    }

    // DELETE
    public Lead delete(Long id) {

        Lead lead = getById(id);

        lead.setIsdeleted(true);

        lead.setIsactive(false);

        return leadRepository.save(lead);
    }

    // GENERATE REFERENCE
    private String generateReference() {

        return "LEAD-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }
}