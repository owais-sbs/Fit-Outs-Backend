package com.fitouts.lead.application;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.application.CompanyService;
import com.fitouts.lead.domain.*;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.account.api.AccountCreateRequest;
import com.fitouts.account.api.AccountResponse;
import com.fitouts.account.application.AccountService;

@Service
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadStatusHistoryRepository historyRepository;
    private final CompanyService companyService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;

    private static final String CLIENT_PASSWORD = "123456";

    public LeadService(LeadRepository leadRepository,
                       LeadStatusHistoryRepository historyRepository,
                       CompanyService companyService,
                       AccountRepository accountRepository,
                       PasswordEncoder passwordEncoder,
                       AccountService accountService) {

        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.companyService = companyService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
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

//        if (status == LeadStatus.LOST &&
//                (lostReason == null || lostReason.isEmpty())) {
//
//            throw new RuntimeException("Lost reason is required");
//        }
        
//        if (status == LeadStatus.CLIENT) {
//
//            AccountCreateRequest request = new AccountCreateRequest();
//
//            request.setFullName(
//                    lead.getClientName() != null
//                            ? lead.getClientName()
//                            : "Client"
//            );
//
//            request.setEmail(lead.getEmail());
//
//            request.setPassword("123456");
//
//            request.setPhone(lead.getPhone());
//
//            request.setCompanyName(
//                    lead.getCompanyEntity() != null
//                            ? lead.getCompanyEntity().getCompanyName()
//                            : null
//            );
//
//            request.setCompanyUuid(
//                    lead.getCompanyEntity() != null
//                            ? lead.getCompanyEntity().getUuid()
//                            : null
//            );
//
//            request.setRoles(Set.of(Role.CLIENT));
//
//                accountService.create(request);
//        }

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

    // CONVERT LEAD TO CLIENT
//    public Lead convertToClient(Long leadId) {
//
//        Lead lead = leadRepository.findById(leadId)
//                .orElseThrow(() -> new NotFoundException("Lead not found"));
//
//        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
//            throw new BadRequestException("Lead email is required to convert to client");
//        }
//
//        String email = lead.getEmail().trim().toLowerCase();
//
//        Account account = accountRepository.findByEmail(email).orElseGet(() -> {
//            Account newAccount = new Account();
//            newAccount.setFullName(lead.getClientName() != null ? lead.getClientName().trim() : "Client");
//            newAccount.setEmail(email);
//            newAccount.setPassword(passwordEncoder.encode("123456"));
//            newAccount.setPhone(lead.getPhone() != null ? lead.getPhone().trim() : null);
//            newAccount.setCompanyName(
//                    lead.getCompanyEntity() != null ? lead.getCompanyEntity().getCompanyName() : null);
//            
//            newAccount.setIsActive(true);
//            newAccount.setRoles(new HashSet<>(List.of(Role.CLIENT)));
//            return accountRepository.save(newAccount);
//        });
//
//        if (!account.getRoles().contains(Role.CLIENT)) {
//            account.getRoles().add(Role.CLIENT);
//            account.setIsActive(true);
//            accountRepository.save(account);
//        }
//
//        lead.setStatus(LeadStatus.CLIENT);
//        lead.setUpdatedAt(LocalDateTime.now());
//        lead.setLastActivityDate(LocalDateTime.now());
//
//        Lead updated = leadRepository.save(lead);
//
//        LeadStatusHistory history = new LeadStatusHistory();
//        history.setLeadId(leadId);
//        history.setStatus(LeadStatus.CLIENT);
//        history.setNotes("Converted to client");
//        history.setCreatedAt(LocalDateTime.now());
//        historyRepository.save(history);
//
//        Hibernate.initialize(updated.getCompanyEntity());
//        if (updated.getCompanyEntity() != null) {
//            Hibernate.initialize(updated.getCompanyEntity().getSubscriptionPlan());
//        }
//        Hibernate.initialize(updated.getAssignedTo());
//
//        return updated;
//    }
    
//    public AccountResponse createAccount(Long leadId) {
//
//        Lead lead = leadRepository.findById(leadId)
//                .orElseThrow(() -> new NotFoundException("Lead not found"));
//
//        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
//            throw new BadRequestException("Lead email is required");
//        }
//
//        String email = lead.getEmail().trim().toLowerCase();
//
//        if (accountRepository.findByEmail(email).isPresent()) {
//            throw new ConflictException("Account already exists with email: " + email);
//        }
//
//        AccountCreateRequest request = new AccountCreateRequest();
//
//        request.setFullName(
//                lead.getClientName() != null
//                        ? lead.getClientName()
//                        : "Client"
//        );
//
//        request.setEmail(email);
//
//        request.setPassword("123456");
//
//        request.setPhone(lead.getPhone());
//
//        request.setCompanyName(
//                lead.getCompanyEntity() != null
//                        ? lead.getCompanyEntity().getCompanyName()
//                        : null
//        );
//
//        request.setCompanyUuid(
//                lead.getCompanyEntity() != null
//                        ? lead.getCompanyEntity().getUuid()
//                        : null
//        );
//
//        request.setRoles(Set.of(Role.CLIENT));
//
//        AccountResponse account = accountService.create(request);
//        
//        lead.setAccountCreated(true);
//
//        return account;
//    }
    
    public Lead convertToClient(Long leadId) {

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));

        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
            throw new BadRequestException("Lead email is required to convert to client");
        }

        String email = lead.getEmail().trim().toLowerCase();

        if (accountRepository.findByEmail(email).isEmpty()) {

            AccountCreateRequest request = new AccountCreateRequest();

            request.setFullName(
                    lead.getClientName() != null
                            ? lead.getClientName()
                            : "Client"
            );

            request.setEmail(email);

            request.setPassword("123456");

            request.setPhone(lead.getPhone());

            request.setCompanyName(
                    lead.getCompanyEntity() != null
                            ? lead.getCompanyEntity().getCompanyName()
                            : null
            );

            request.setCompanyUuid(
                    lead.getCompanyEntity() != null
                            ? lead.getCompanyEntity().getUuid()
                            : null
            );

            request.setRoles(Set.of(Role.CLIENT));

            accountService.create(request);

        } else {

            Account account = accountRepository.findByEmail(email).get();

            if (!account.getRoles().contains(Role.CLIENT)) {
                account.getRoles().add(Role.CLIENT);
            }

            account.setIsActive(true);

            accountRepository.save(account);
        }

        lead.setStatus(LeadStatus.CLIENT);
        lead.setAccountCreated(true);
        lead.setUpdatedAt(LocalDateTime.now());
        lead.setLastActivityDate(LocalDateTime.now());

        Lead updated = leadRepository.save(lead);

        LeadStatusHistory history = new LeadStatusHistory();
        history.setLeadId(leadId);
        history.setStatus(LeadStatus.CLIENT);
        history.setNotes("Converted to client");
        history.setCreatedAt(LocalDateTime.now());

        historyRepository.save(history);

        Hibernate.initialize(updated.getCompanyEntity());

        if (updated.getCompanyEntity() != null) {
            Hibernate.initialize(updated.getCompanyEntity().getSubscriptionPlan());
        }

        Hibernate.initialize(updated.getAssignedTo());

        return updated;
    }
    
    public AccountResponse createAccount(Long leadId) {

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));

        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
            throw new BadRequestException("Lead email is required");
        }

        String email = lead.getEmail().trim().toLowerCase();

        if (accountRepository.findByEmail(email).isPresent()) {
            throw new ConflictException(
                    "Account already exists with email: " + email
            );
        }

        AccountCreateRequest request = new AccountCreateRequest();

        request.setFullName(
                lead.getClientName() != null
                        ? lead.getClientName()
                        : "Client"
        );

        request.setEmail(email);

        request.setPassword("123456");

        request.setPhone(lead.getPhone());

        request.setCompanyName(
                lead.getCompanyEntity() != null
                        ? lead.getCompanyEntity().getCompanyName()
                        : null
        );

        request.setCompanyUuid(
                lead.getCompanyEntity() != null
                        ? lead.getCompanyEntity().getUuid()
                        : null
        );

        request.setRoles(Set.of(Role.CLIENT));

        AccountResponse account = accountService.create(request);

        lead.setAccountCreated(true);
        lead.setUpdatedAt(LocalDateTime.now());

        leadRepository.save(lead);

        return account;
    }
}