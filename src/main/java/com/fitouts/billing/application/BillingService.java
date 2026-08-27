package com.fitouts.billing.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.billing.api.BillingMilestoneRequest;
import com.fitouts.billing.api.BillingMilestoneResponse;
import com.fitouts.billing.api.ClientInvoiceResponse;
import com.fitouts.billing.api.PaymentRejectRequest;
import com.fitouts.billing.api.PaymentRequestResponse;
import com.fitouts.billing.api.RequestPaymentBody;
import com.fitouts.billing.domain.BillingMilestone;
import com.fitouts.billing.domain.BillingMilestoneRepository;
import com.fitouts.billing.domain.BillingStatus;
import com.fitouts.billing.domain.PaymentRequest;
import com.fitouts.billing.domain.PaymentRequestRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingMilestoneRepository milestoneRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final ProjectService projectService;
    private final ScheduleActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public List<BillingMilestoneResponse> listMilestones(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return milestoneRepository
                .findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toMilestoneResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BillingMilestoneResponse getMilestone(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        return toMilestoneResponse(requireMilestone(uuid, projectId));
    }

    @Transactional
    public BillingMilestoneResponse createMilestone(Long projectId, BillingMilestoneRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("name is required");
        }

        BillingMilestone milestone = new BillingMilestone();
        milestone.setProjectId(project.getId());
        milestone.setCompanyId(CompanyContext.get());
        milestone.setName(request.getName().trim());
        milestone.setAmount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO);
        milestone.setDueDate(request.getDueDate());
        milestone.setLinkedActivityUuid(request.getLinkedActivityUuid());
        milestone.setStatus(request.getStatus() != null ? request.getStatus() : BillingStatus.DRAFT);
        milestone.setPercentCompleteRequired(request.getPercentCompleteRequired());
        milestone.setCreatedBy(principal.getAccountId());
        return toMilestoneResponse(milestoneRepository.save(milestone));
    }

    @Transactional
    public BillingMilestoneResponse updateMilestone(Long projectId, UUID uuid, BillingMilestoneRequest request) {
        requireStaff();
        requireProject(projectId);
        BillingMilestone milestone = requireMilestone(uuid, projectId);
        if (request == null) {
            return toMilestoneResponse(milestone);
        }
        if (StringUtils.hasText(request.getName())) {
            milestone.setName(request.getName().trim());
        }
        if (request.getAmount() != null) {
            milestone.setAmount(request.getAmount());
        }
        if (request.getDueDate() != null) {
            milestone.setDueDate(request.getDueDate());
        }
        if (request.getLinkedActivityUuid() != null) {
            milestone.setLinkedActivityUuid(request.getLinkedActivityUuid());
        }
        if (request.getStatus() != null) {
            milestone.setStatus(request.getStatus());
        }
        if (request.getPercentCompleteRequired() != null) {
            milestone.setPercentCompleteRequired(request.getPercentCompleteRequired());
        }
        return toMilestoneResponse(milestoneRepository.save(milestone));
    }

    @Transactional
    public void deleteMilestone(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        milestoneRepository.delete(requireMilestone(uuid, projectId));
    }

    @Transactional
    public PaymentRequestResponse requestPayment(Long projectId, UUID milestoneUuid, RequestPaymentBody body) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        BillingMilestone milestone = requireMilestone(milestoneUuid, projectId);

        PaymentRequest pr = new PaymentRequest();
        pr.setMilestoneUuid(milestone.getUuid());
        pr.setProjectId(milestone.getProjectId());
        pr.setCompanyId(milestone.getCompanyId());
        pr.setAmount(body != null && body.getAmount() != null ? body.getAmount() : milestone.getAmount());
        pr.setNotes(body != null ? body.getNotes() : null);
        pr.setStatus(BillingStatus.DRAFT);
        pr.setRequestedBy(principal.getAccountId());
        pr = paymentRequestRepository.save(pr);
        return toPaymentResponse(pr, milestone.getName());
    }

    @Transactional
    public PaymentRequestResponse submit(UUID paymentRequestUuid) {
        AuthPrincipal principal = requireStaff();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        if (pr.getStatus() != BillingStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT payment requests can be submitted");
        }
        pr.setStatus(BillingStatus.PENDING_PM);
        if (pr.getRequestedBy() == null) {
            pr.setRequestedBy(principal.getAccountId());
        }
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.PENDING_PM);
        milestoneRepository.save(milestone);
        return toPaymentResponse(paymentRequestRepository.save(pr), milestone.getName());
    }

    @Transactional
    public PaymentRequestResponse approve(UUID paymentRequestUuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        if (pr.getStatus() != BillingStatus.PENDING_PM) {
            throw new BadRequestException("Only PENDING_PM payment requests can be approved");
        }
        pr.setStatus(BillingStatus.ISSUED);
        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.ISSUED);
        milestoneRepository.save(milestone);
        return toPaymentResponse(paymentRequestRepository.save(pr), milestone.getName());
    }

    @Transactional
    public PaymentRequestResponse reject(UUID paymentRequestUuid, PaymentRejectRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        if (pr.getStatus() != BillingStatus.PENDING_PM) {
            throw new BadRequestException("Only PENDING_PM payment requests can be rejected");
        }
        pr.setStatus(BillingStatus.DRAFT);
        pr.setNotes(appendReason(pr.getNotes(), request.getReason().trim()));
        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.DRAFT);
        milestoneRepository.save(milestone);
        return toPaymentResponse(paymentRequestRepository.save(pr), milestone.getName());
    }

    @Transactional
    public PaymentRequestResponse markPaid(UUID paymentRequestUuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        if (pr.getStatus() != BillingStatus.ISSUED && pr.getStatus() != BillingStatus.PART_PAID) {
            throw new BadRequestException("Only ISSUED or PART_PAID payment requests can be marked paid");
        }
        pr.setStatus(BillingStatus.PAID);
        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.PAID);
        milestoneRepository.save(milestone);
        return toPaymentResponse(paymentRequestRepository.save(pr), milestone.getName());
    }

    @Transactional(readOnly = true)
    public List<ClientInvoiceResponse> listClientInvoices(Long projectId) {
        requireAuthenticated();
        Project project = requireProject(projectId);
        return paymentRequestRepository
                .findByProjectIdAndCompanyIdAndStatusInOrderByCreatedAtDesc(
                        project.getId(),
                        CompanyContext.get(),
                        EnumSet.of(BillingStatus.ISSUED, BillingStatus.PAID, BillingStatus.PART_PAID))
                .stream()
                .map(pr -> {
                    String name = milestoneRepository.findByUuidAndCompanyId(pr.getMilestoneUuid(), pr.getCompanyId())
                            .map(BillingMilestone::getName)
                            .orElse(null);
                    return ClientInvoiceResponse.builder()
                            .paymentRequestUuid(pr.getUuid())
                            .milestoneUuid(pr.getMilestoneUuid())
                            .milestoneName(name)
                            .amount(pr.getAmount())
                            .status(pr.getStatus())
                            .notes(pr.getNotes())
                            .issuedAt(pr.getDecidedAt() != null ? pr.getDecidedAt() : pr.getCreatedAt())
                            .updatedAt(pr.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    /**
     * After schedule progress is saved: DRAFT milestones linked to the activity whose
     * percent_complete_required has been met become PENDING_PM with an auto payment request.
     */
    @Transactional
    public void evaluateTriggersForActivity(UUID activityUuid) {
        if (activityUuid == null) {
            return;
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            return;
        }
        ScheduleActivity activity = activityRepository.findByUuidAndCompanyId(activityUuid, companyId).orElse(null);
        if (activity == null) {
            return;
        }
        BigDecimal percent = BigDecimal.valueOf(activity.getPercentComplete());
        List<BillingMilestone> candidates = milestoneRepository
                .findByLinkedActivityUuidAndCompanyIdAndStatus(activityUuid, companyId, BillingStatus.DRAFT);
        for (BillingMilestone milestone : candidates) {
            if (milestone.getPercentCompleteRequired() == null) {
                continue;
            }
            if (percent.compareTo(milestone.getPercentCompleteRequired()) < 0) {
                continue;
            }
            // Skip if a non-draft payment request already exists for this milestone
            boolean alreadyTriggered = paymentRequestRepository
                    .findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(milestone.getUuid(), companyId)
                    .stream()
                    .anyMatch(pr -> pr.getStatus() != BillingStatus.DRAFT);
            if (alreadyTriggered) {
                continue;
            }

            PaymentRequest pr = new PaymentRequest();
            pr.setMilestoneUuid(milestone.getUuid());
            pr.setProjectId(milestone.getProjectId());
            pr.setCompanyId(milestone.getCompanyId());
            pr.setAmount(milestone.getAmount());
            pr.setNotes("Auto-triggered at " + activity.getPercentComplete() + "% complete");
            pr.setStatus(BillingStatus.PENDING_PM);
            pr.setRequestedBy(null);
            paymentRequestRepository.save(pr);

            milestone.setStatus(BillingStatus.PENDING_PM);
            milestoneRepository.save(milestone);
        }
    }

    private String appendReason(String notes, String reason) {
        if (!StringUtils.hasText(notes)) {
            return "Rejected: " + reason;
        }
        return notes + "\nRejected: " + reason;
    }

    private BillingMilestone requireMilestone(UUID uuid, Long projectId) {
        BillingMilestone milestone = requireMilestoneByUuid(uuid);
        if (!milestone.getProjectId().equals(projectId)) {
            throw new BadRequestException("Milestone does not belong to this project");
        }
        return milestone;
    }

    private BillingMilestone requireMilestoneByUuid(UUID uuid) {
        return milestoneRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Billing milestone not found"));
    }

    private PaymentRequest requirePayment(UUID uuid) {
        return paymentRequestRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Payment request not found"));
    }

    private BillingMilestoneResponse toMilestoneResponse(BillingMilestone m) {
        return BillingMilestoneResponse.builder()
                .uuid(m.getUuid())
                .projectId(m.getProjectId())
                .companyId(m.getCompanyId())
                .name(m.getName())
                .amount(m.getAmount())
                .dueDate(m.getDueDate())
                .linkedActivityUuid(m.getLinkedActivityUuid())
                .status(m.getStatus())
                .percentCompleteRequired(m.getPercentCompleteRequired())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private PaymentRequestResponse toPaymentResponse(PaymentRequest pr, String milestoneName) {
        return PaymentRequestResponse.builder()
                .uuid(pr.getUuid())
                .milestoneUuid(pr.getMilestoneUuid())
                .projectId(pr.getProjectId())
                .companyId(pr.getCompanyId())
                .amount(pr.getAmount())
                .status(pr.getStatus())
                .notes(pr.getNotes())
                .requestedBy(pr.getRequestedBy())
                .decidedBy(pr.getDecidedBy())
                .decidedAt(pr.getDecidedAt())
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .milestoneName(milestoneName)
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requirePmOrAdmin() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("PM/Admin access required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER)
                || principal.getRoles().contains(Role.FINANCE);
        if (!allowed) {
            throw new ForbiddenException("PM/Admin access required");
        }
        return principal;
    }
}
