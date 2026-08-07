package com.fitouts.checklist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.checklist.domain.ChecklistTemplate;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitAssignment;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.dto.SiteVisitChecklistScopeRequest;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitResponse;
import com.fitouts.checklist.mapper.SiteVisitMapper;
import com.fitouts.checklist.repository.ChecklistTemplateRepository;
import com.fitouts.checklist.repository.SiteVisitAssignmentRepository;
import com.fitouts.checklist.repository.SiteVisitLocationDetailsRepository;
import com.fitouts.checklist.repository.SiteVisitRepository;
import com.fitouts.company.application.CompanyService;
import com.fitouts.employee.domain.Employee;
import com.fitouts.employee.domain.EmployeeRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitService {

    private final SiteVisitRepository repository;
    private final SiteVisitLocationDetailsRepository locationDetailsRepository;
    private final SiteVisitMapper mapper;
    private final CompanyService companyService;
    private final SiteVisitAssignmentRepository assignmentRepository;
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final ChecklistTemplateRepository checklistTemplateRepository;

    private static final String DEFAULT_CHECKLIST_NAME = "JCT Renovation Checklist";
    
    @Transactional
    public SiteVisitResponse create(SiteVisitCreateRequest request) {
    	SiteVisit siteVisit = mapper.toEntity(request);

    	UUID companyId = CompanyContext.get();
    	if (companyId == null) {
    	    throw new BadRequestException("Company context is required to schedule a site visit");
    	}
    	siteVisit.setCompany(companyService.getCompany(companyId));
    	if (siteVisit.getChecklistTemplateUuid() == null) {
    	    checklistTemplateRepository
    	            .findFirstByCompanyUuidAndNameIgnoreCase(companyId, DEFAULT_CHECKLIST_NAME)
    	            .map(ChecklistTemplate::getUuid)
    	            .ifPresent(siteVisit::setChecklistTemplateUuid);
    	}

    	SiteVisit savedSiteVisit = repository.save(siteVisit);

    	List<SiteVisitAssignment> assignments = new ArrayList<>();
    	for (Long employeeId : request.getEmployeeIds()) {

    		Employee employee = employeeRepository.findById(employeeId)
    		        .orElseThrow(() -> new NotFoundException("Employee not found"));

    		Account account = accountRepository.findById(employee.getAccountId())
    		        .orElseThrow(() -> new NotFoundException("Account not found"));

    	    SiteVisitAssignment assignment = new SiteVisitAssignment();

    	    assignment.setSiteVisit(savedSiteVisit);
    	    assignment.setEmployee(account);

    	    assignmentRepository.save(assignment);
    	    assignments.add(assignment);
    	}
    	savedSiteVisit.setAssignments(assignments);

    	SiteVisit refreshed = repository.findById(savedSiteVisit.getUuid())
    	        .orElseThrow(() -> new NotFoundException("Site visit not found"));

    	return mapper.toResponse(refreshed);
    }

    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getAll() {
        UUID companyId = CompanyContext.get();
        List<SiteVisit> visits = companyId != null
                ? repository.findByCompanyUuid(companyId)
                : repository.findAll();
        return visits.stream()
                .map(visit -> {
                    try {
                        return mapper.toResponse(visit);
                    } catch (Exception ex) {
                        // Skip corrupt rows so one bad visit does not empty the whole list
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteVisitResponse getByUuid(UUID uuid) {
        return mapper.toResponse(getSiteVisit(uuid));
    }

    @Transactional
    public SiteVisitResponse addLocationDetails(UUID uuid, SiteVisitLocationDetailsRequest request) {
        SiteVisit siteVisit = getSiteVisit(uuid);
        if (locationDetailsRepository.existsBySiteVisitUuid(uuid)) {
            throw new ConflictException("Site visit location details already exist");
        }

        SiteVisitLocationDetails details = mapper.toLocationEntity(request);
        siteVisit.setLocationDetails(details);
        return mapper.toResponse(repository.save(siteVisit));
    }

    @Transactional
    public SiteVisitResponse updateChecklistScope(UUID uuid, SiteVisitChecklistScopeRequest request) {
        SiteVisit siteVisit = getSiteVisit(uuid);
        if (siteVisit.getStatus() == SiteVisitStatus.COMPLETED) {
            throw new ConflictException("Cannot update checklist scope on a completed site visit");
        }
        if (siteVisit.getStatus() == SiteVisitStatus.CANCELLED) {
            throw new ConflictException("Cannot update checklist scope on a cancelled site visit");
        }
        mapper.applyChecklistScope(siteVisit, request);
        return mapper.toResponse(repository.save(siteVisit));
    }

    @Transactional(readOnly = true)
    public SiteVisit getSiteVisit(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Site visit not found"));
    }
    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getEmployeeSiteVisits(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        Long accountId = employee.getAccountId();

        return repository.findByAssignedToId(accountId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
