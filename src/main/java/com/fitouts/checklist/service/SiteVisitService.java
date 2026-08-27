package com.fitouts.checklist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.security.AuthPrincipal;
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
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.PortalAccessHelper;

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
        private final LeadRepository leadRepository;
        private final PortalAccessHelper portalAccess;
        private final SiteVisitNotificationEmailService siteVisitNotificationEmailService;

    private static final String DEFAULT_CHECKLIST_NAME = "JCT Renovation Checklist";

    @Transactional
    public SiteVisitResponse create(SiteVisitCreateRequest request) {
        portalAccess.requireStaff();
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

        siteVisitNotificationEmailService.sendInitialNotification(refreshed.getUuid());

        return mapper.toResponse(refreshed);
    }

    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getAll() {
        AuthPrincipal principal = portalAccess.requirePrincipal();
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }

        List<SiteVisit> visits;
        if (portalAccess.isPureClient(principal)) {
            visits = repository.findByCompanyUuidAndLeadEmail(companyId, principal.getEmail());
        } else if (portalAccess.isSiteEngineer(principal)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SUPER_ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.QS)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SENIOR_QS)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.PROJECT_MANAGER)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.BUSINESS_OWNER)) {
            visits = repository.findByCompanyUuidAndAssigneeAccountId(companyId, principal.getAccountId());
        } else {
            visits = repository.findByCompanyUuid(companyId);
        }

        return mapper.toListResponses(visits);
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
        SiteVisit saved = repository.save(siteVisit);
        siteVisitNotificationEmailService.sendLocationUpdateNotification(uuid);
        return mapper.toResponse(saved);
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
        SiteVisit visit = repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Site visit not found"));
        assertCanAccess(visit);
        return visit;
    }

    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getEmployeeSiteVisits(Long employeeId) {
        AuthPrincipal principal = portalAccess.requirePrincipal();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        // Site engineers may only query their own assignments
        if (portalAccess.isSiteEngineer(principal)
                && portalAccess.isPureClient(principal) == false
                && !Objects.equals(employee.getAccountId(), principal.getAccountId())
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SUPER_ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.PROJECT_MANAGER)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.QS)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SENIOR_QS)) {
            throw new ForbiddenException("Not your assignments");
        }

        Long accountId = employee.getAccountId();
        return repository.findByAssignedToId(accountId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SiteVisitResponse> getMyAssignedVisits() {
        AuthPrincipal principal = portalAccess.requirePrincipal();
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return mapper.toListResponses(
                repository.findByCompanyUuidAndAssigneeAccountId(companyId, principal.getAccountId()));
    }

    private void assertCanAccess(SiteVisit visit) {
        AuthPrincipal principal = portalAccess.requirePrincipal();
        UUID companyId = CompanyContext.get();
        if (visit.getCompany() == null || companyId == null
                || !Objects.equals(visit.getCompany().getUuid(), companyId)) {
            throw new NotFoundException("Site visit not found");
        }

        if (portalAccess.isPureClient(principal)) {
            Lead lead = leadRepository.findById(visit.getLeadId()).orElse(null);
            if (lead == null || lead.getEmail() == null
                    || !lead.getEmail().trim().equalsIgnoreCase(principal.getEmail())) {
                throw new ForbiddenException("Not your site visit");
            }
            return;
        }

        if (portalAccess.isSiteEngineer(principal)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SUPER_ADMIN)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.QS)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.SENIOR_QS)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.PROJECT_MANAGER)
                && !portalAccess.hasRole(principal, com.fitouts.auth.domain.Role.BUSINESS_OWNER)) {
            boolean assigned = visit.getAssignedTo() != null
                    && Objects.equals(visit.getAssignedTo().getId(), principal.getAccountId());
            if (!assigned && visit.getAssignments() != null) {
                assigned = visit.getAssignments().stream()
                        .anyMatch(a -> a.getEmployee() != null
                                && Objects.equals(a.getEmployee().getId(), principal.getAccountId()));
            }
            if (!assigned) {
                throw new ForbiddenException("Not your assigned site visit");
            }
        }
    }
}
