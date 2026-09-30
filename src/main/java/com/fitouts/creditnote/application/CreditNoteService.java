package com.fitouts.creditnote.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.creditnote.api.CreditNoteRequest;
import com.fitouts.creditnote.api.CreditNoteResponse;
import com.fitouts.creditnote.domain.CreditNote;
import com.fitouts.creditnote.domain.CreditNoteRepository;
import com.fitouts.creditnote.domain.CreditNoteStatus;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private static final Set<Role> STAFF = EnumSet.of(
            Role.SUPER_ADMIN, Role.ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER,
            Role.QS, Role.SENIOR_QS, Role.FINANCE);

    private final CreditNoteRepository creditNoteRepository;
    private final ProjectService projectService;
    private final CommercialLifecycleService commercialLifecycleService;
    private final CommercialApprovalService commercialApprovalService;
    private final ProjectCommercialRepository commercialRepository;
    private final BoqProjectRules boqProjectRules;

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return creditNoteRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), requireCompany())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CreditNoteResponse create(Long projectId, CreditNoteRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        commercialLifecycleService.assertCommercialMutable(project.getId());
        String title = requireTitle(request);
        BigDecimal amount = requireAmount(request);

        CreditNote note = new CreditNote();
        note.setProjectId(project.getId());
        note.setCompanyId(requireCompany());
        note.setNoteNumber(nextNumber(project.getId()));
        note.setTitle(title);
        note.setReason(trimToNull(request.getReason()));
        note.setAmount(amount);
        note.setStatus(CreditNoteStatus.DRAFT);
        note.setCreatedBy(principal.getAccountId());
        return toResponse(creditNoteRepository.save(note));
    }

    @Transactional
    public CreditNoteResponse submit(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        commercialLifecycleService.assertCommercialMutable(project.getId());
        CreditNote note = requireNote(uuid, project.getId());
        if (note.getStatus() != CreditNoteStatus.DRAFT && note.getStatus() != CreditNoteStatus.REJECTED) {
            throw new BadRequestException("Only a draft or rejected credit note can be submitted");
        }

        Optional<CommercialApprovalRun> run = commercialApprovalService.startRun(
                CommercialEventType.CREDIT_NOTE,
                note.getUuid(),
                note.getProjectId(),
                note.getAmount(),
                principal.getAccountId());
        if (run.isEmpty()) {
            throw new BadRequestException(
                    "Save a CREDIT_NOTE approval matrix before submitting a credit note");
        }

        note.setStatus(CreditNoteStatus.IN_REVIEW);
        note.setApprovalRunUuid(run.get().getUuid());
        note.setRejectComment(null);
        return toResponse(creditNoteRepository.save(note));
    }

    @Transactional
    public void markApproved(UUID uuid) {
        CreditNote note = creditNoteRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Credit note not found"));
        if (note.getStatus() == CreditNoteStatus.APPROVED) {
            return;
        }
        applyToContract(note);
        note.setStatus(CreditNoteStatus.APPROVED);
        note.setApprovedAt(OffsetDateTime.now());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            note.setApprovedBy(principal.getAccountId());
        }
        creditNoteRepository.save(note);
    }

    @Transactional
    public void markRejected(UUID uuid, String comment) {
        CreditNote note = creditNoteRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Credit note not found"));
        if (note.getStatus() == CreditNoteStatus.APPROVED) {
            return;
        }
        note.setStatus(CreditNoteStatus.REJECTED);
        note.setRejectComment(trimToNull(comment));
        creditNoteRepository.save(note);
    }

    private void applyToContract(CreditNote note) {
        Project project = projectService.getById(note.getProjectId());
        ProjectCommercial commercial = commercialRepository
                .findFirstByProjectIdAndCompanyIdOrderByUuidAsc(project.getId(), note.getCompanyId())
                .orElseGet(() -> newCommercial(project));
        BigDecimal current = commercial.getCurrentContractValue() != null
                ? commercial.getCurrentContractValue() : BigDecimal.ZERO;
        BigDecimal next = current.subtract(note.getAmount());
        commercial.setCurrentContractValue(next);
        BigDecimal cost = commercial.getCurrentCost() != null ? commercial.getCurrentCost() : BigDecimal.ZERO;
        commercial.setCurrentMargin(next.subtract(cost));
        commercialRepository.save(commercial);
    }

    private ProjectCommercial newCommercial(Project project) {
        BigDecimal base = boqProjectRules.findApproved(project.getId())
                .map(BoqDocument::getGrandTotal)
                .filter(total -> total != null)
                .orElse(project.getBudget() != null ? project.getBudget() : BigDecimal.ZERO);
        ProjectCommercial commercial = new ProjectCommercial();
        commercial.setProjectId(project.getId());
        commercial.setCompanyId(project.getCompanyId());
        commercial.setOriginalContractValue(base);
        commercial.setCurrentContractValue(base);
        commercial.setCurrentCost(BigDecimal.ZERO);
        commercial.setCurrentMargin(base);
        return commercialRepository.save(commercial);
    }

    private String nextNumber(Long projectId) {
        long count = creditNoteRepository.countByProjectIdAndCompanyId(projectId, requireCompany());
        return String.format("CN-%04d", count + 1);
    }

    private CreditNote requireNote(UUID uuid, Long projectId) {
        CreditNote note = creditNoteRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Credit note not found"));
        if (!note.getProjectId().equals(projectId)) {
            throw new BadRequestException("Credit note does not belong to this project");
        }
        return note;
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = requireCompany();
        if (project.getCompanyId() != null && !project.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("Project is not in this company");
        }
        return project;
    }

    private String requireTitle(CreditNoteRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("title is required");
        }
        return request.getTitle().trim();
    }

    private BigDecimal requireAmount(CreditNoteRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new BadRequestException("amount must be greater than zero");
        }
        return request.getAmount();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Not authenticated");
        }
        if (principal.getRoles() == null || principal.getRoles().stream().noneMatch(STAFF::contains)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private CreditNoteResponse toResponse(CreditNote note) {
        return CreditNoteResponse.builder()
                .uuid(note.getUuid())
                .projectId(note.getProjectId())
                .noteNumber(note.getNoteNumber())
                .title(note.getTitle())
                .reason(note.getReason())
                .amount(note.getAmount())
                .status(note.getStatus())
                .approvalRunUuid(note.getApprovalRunUuid())
                .rejectComment(note.getRejectComment())
                .createdAt(note.getCreatedAt())
                .approvedAt(note.getApprovedAt())
                .build();
    }
}
