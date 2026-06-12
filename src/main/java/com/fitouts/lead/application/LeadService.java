package com.fitouts.lead.application;

import com.fitouts.company.application.CompanyService;
import com.fitouts.lead.domain.*;
import com.fitouts.shared.context.CompanyContext;
import org.hibernate.Hibernate;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
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

        Hibernate.initialize(saved.getCompanyEntity());
        if (saved.getCompanyEntity() != null) {
            Hibernate.initialize(saved.getCompanyEntity().getSubscriptionPlan());
        }
        Hibernate.initialize(saved.getAssignedTo());

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

        Hibernate.initialize(updated.getCompanyEntity());
        if (updated.getCompanyEntity() != null) {
            Hibernate.initialize(updated.getCompanyEntity().getSubscriptionPlan());
        }
        Hibernate.initialize(updated.getAssignedTo());

        return updated;
    }

    // FILTERED PAGINATION — scoped to current company
    @Transactional(readOnly = true)
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

        Page<Lead> leads = leadRepository.findAll(
                LeadSpecification.filterLeads(filter),
                pageable
        );

        for (Lead lead : leads) {
            Hibernate.initialize(lead.getCompanyEntity());
            if (lead.getCompanyEntity() != null) {
                Hibernate.initialize(lead.getCompanyEntity().getSubscriptionPlan());
            }
            Hibernate.initialize(lead.getAssignedTo());
        }

        return leads;
    }

    // GET BY ID
    @Transactional(readOnly = true)
    public Lead getById(Long id) {

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));

        Hibernate.initialize(lead.getCompanyEntity());
        if (lead.getCompanyEntity() != null) {
            Hibernate.initialize(lead.getCompanyEntity().getSubscriptionPlan());
        }
        Hibernate.initialize(lead.getAssignedTo());

        return lead;
    }

    // DELETE
    public Lead delete(Long id) {

        Lead lead = getById(id);

        lead.setIsdeleted(true);

        lead.setIsactive(false);

        Lead deleted = leadRepository.save(lead);

        Hibernate.initialize(deleted.getCompanyEntity());
        if (deleted.getCompanyEntity() != null) {
            Hibernate.initialize(deleted.getCompanyEntity().getSubscriptionPlan());
        }
        Hibernate.initialize(deleted.getAssignedTo());

        return deleted;
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