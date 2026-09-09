package com.fitouts.billing.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.billing.api.BillingMilestoneRequest;
import com.fitouts.billing.api.BillingMilestoneResponse;
import com.fitouts.billing.api.ClientInvoiceResponse;
import com.fitouts.billing.api.PaymentApproveRequest;
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
    private final BillingApprovalEventService billingApprovalEventService;
    private final ProjectService projectService;
    private final ScheduleActivityRepository activityRepository;
    private final BillingPaymentEmailService billingPaymentEmailService;
    private final AccountRepository accountRepository;
    private final Set<UUID> activeReminderProcessing = ConcurrentHashMap.newKeySet();

    @Transactional(readOnly = true)
    public List<BillingMilestoneResponse> listMilestones(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return milestoneRepository
                .findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toMilestoneResponseWithPayment)
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
        AuthPrincipal principal = requireFinanceOrAdmin();
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
        milestone.setLinkedActivityUuid(parseOptionalUuid(request.getLinkedActivityUuid(), "linkedActivityUuid"));
        milestone.setStatus(request.getStatus() != null ? request.getStatus() : BillingStatus.DRAFT);
        milestone.setPercentCompleteRequired(request.getPercentCompleteRequired());
        milestone.setCreatedBy(principal.getAccountId());
        return toMilestoneResponse(milestoneRepository.save(milestone));
    }

    @Transactional
    public BillingMilestoneResponse updateMilestone(Long projectId, UUID uuid, BillingMilestoneRequest request) {
        requireFinanceOrAdmin();
        requireProject(projectId);
        BillingMilestone milestone = requireMilestone(uuid, projectId);
        if (milestone.getStatus() != BillingStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT milestones can be edited");
        }
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
            milestone.setLinkedActivityUuid(parseOptionalUuid(request.getLinkedActivityUuid(), "linkedActivityUuid"));
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
        requireFinanceOrAdmin();
        requireProject(projectId);
        BillingMilestone milestone = requireMilestone(uuid, projectId);
        if (milestone.getStatus() != BillingStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT milestones can be deleted");
        }
        milestoneRepository.delete(milestone);
    }

    /**
     * Finance submits a manually created milestone into the approval chain (PM, then Director).
     * Client notification is sent only after Director approval.
     */
    @Transactional
    public PaymentRequestResponse submitForApproval(Long projectId, UUID milestoneUuid, RequestPaymentBody body) {
        AuthPrincipal principal = requireFinanceOrAdmin();
        requireProject(projectId);
        BillingMilestone milestone = requireMilestone(milestoneUuid, projectId);
        if (milestone.getStatus() != BillingStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT milestones can be submitted for approval");
        }

        UUID companyId = requireCompany();
        PaymentRequest pr = paymentRequestRepository
                .findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(milestone.getUuid(), companyId)
                .stream()
                .filter(existing -> existing.getStatus() == BillingStatus.DRAFT)
                .findFirst()
                .orElseGet(PaymentRequest::new);

        pr.setMilestoneUuid(milestone.getUuid());
        pr.setProjectId(milestone.getProjectId());
        pr.setCompanyId(milestone.getCompanyId());
        pr.setAmount(body != null && body.getAmount() != null ? body.getAmount() : milestone.getAmount());
        pr.setNotes(body != null ? body.getNotes() : null);
        pr.setStatus(BillingStatus.PENDING_PM);
        pr.setRequestedBy(principal.getAccountId());
        pr = paymentRequestRepository.save(pr);

        milestone.setStatus(BillingStatus.PENDING_PM);
        milestoneRepository.save(milestone);
        recordEvent(pr, "SUBMITTED", "FINANCE", principal.getAccountId(), pr.getNotes());

        return toPaymentResponse(pr, milestone);
    }

    @Transactional
    public PaymentRequestResponse requestPayment(Long projectId, UUID milestoneUuid, RequestPaymentBody body) {
        AuthPrincipal principal = requireFinanceOrAdmin();
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

        return toPaymentResponse(pr, milestone);
    }

    @Transactional
    public PaymentRequestResponse submit(UUID paymentRequestUuid) {
        AuthPrincipal principal = requireFinanceOrAdmin();
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
        PaymentRequest saved = paymentRequestRepository.save(pr);
        recordEvent(saved, "SUBMITTED", "FINANCE", principal.getAccountId(), saved.getNotes());
        return toPaymentResponse(saved, milestone);
    }

    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> listInbox() {
        AuthPrincipal principal = requireAuthenticated();
        EnumSet<BillingStatus> statuses = inboxStatusesFor(principal);
        if (statuses.isEmpty()) {
            throw new ForbiddenException("No billing milestone inbox for this role");
        }
        UUID companyId = requireCompany();
        return paymentRequestRepository
                .findByCompanyIdAndStatusInOrderByCreatedAtDesc(companyId, statuses)
                .stream()
                .map(pr -> {
                    BillingMilestone milestone = milestoneRepository
                            .findByUuidAndCompanyId(pr.getMilestoneUuid(), companyId)
                            .orElse(null);
                    return toPaymentResponse(pr, milestone);
                })
                .toList();
    }

    @Transactional
    public PaymentRequestResponse approve(UUID paymentRequestUuid, PaymentApproveRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        Project project = requireProject(milestone.getProjectId());
        String comments = request != null ? request.getComments() : null;
        if (StringUtils.hasText(comments)) {
            pr.setNotes(appendComment(pr.getNotes(), "Approved: " + comments.trim()));
        }

        if (pr.getStatus() == BillingStatus.PENDING_PM) {
            requireProjectManagerRole(principal);
            pr.setStatus(BillingStatus.PENDING_DIRECTOR);
            milestone.setStatus(BillingStatus.PENDING_DIRECTOR);
            paymentRequestRepository.save(pr);
            milestoneRepository.save(milestone);
            recordEvent(pr, "APPROVED", "PM", principal.getAccountId(), comments);
        } else if (pr.getStatus() == BillingStatus.PENDING_DIRECTOR) {
            requireDirectorRole(principal);
            pr.setStatus(BillingStatus.ISSUED);
            milestone.setStatus(BillingStatus.ISSUED);
            paymentRequestRepository.save(pr);
            milestoneRepository.save(milestone);
            recordEvent(pr, "APPROVED", "DIRECTOR", principal.getAccountId(), comments);

            boolean alreadyNotifiedForProject = paymentRequestRepository
                    .findByCompanyIdAndStatusInOrderByCreatedAtDesc(
                            pr.getCompanyId(),
                            EnumSet.of(BillingStatus.ISSUED, BillingStatus.CLIENT_ACCEPTED, BillingStatus.PAID))
                    .stream()
                    .anyMatch(otherPr -> !otherPr.getUuid().equals(pr.getUuid()) && otherPr.getProjectId().equals(pr.getProjectId()));

            BillingPaymentEmailService.SendResult email = BillingPaymentEmailService.SendResult.skipped();
            if (!alreadyNotifiedForProject) {
                email = billingPaymentEmailService.notifyClient(project, milestone, pr);
            }

            pr.setDecidedBy(principal.getAccountId());
            pr.setDecidedAt(OffsetDateTime.now());
            return toPaymentResponse(
                    paymentRequestRepository.save(pr),
                    milestone,
                    email.sent(),
                    email.clientEmail());
        } else if (pr.getStatus() == BillingStatus.ISSUED) {
            pr.setStatus(BillingStatus.CLIENT_ACCEPTED);
            milestone.setStatus(BillingStatus.CLIENT_ACCEPTED);
            recordEvent(pr, "APPROVED", "CLIENT", principal.getAccountId(), comments);
        } else if (pr.getStatus() == BillingStatus.CLIENT_ACCEPTED) {
            recordEvent(pr, "APPROVED", "CLIENT", principal.getAccountId(), comments);
        } else if (pr.getStatus() == BillingStatus.PAID) {
            return toPaymentResponse(pr, milestone);
        } else {
            throw new BadRequestException("Payment request is not in a valid state for approval: " + pr.getStatus());
        }

        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        paymentRequestRepository.save(pr);
        milestoneRepository.save(milestone);
        return toPaymentResponse(pr, milestone);
    }

    @Transactional
    public PaymentRequestResponse acceptPaymentRequest(UUID paymentRequestUuid) {
        AuthPrincipal principal = requireAuthenticated();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        Project project = requireProject(milestone.getProjectId());

        // Client acceptance is a one-time transition.
        // Only ISSUED proposals can be accepted.
        if (pr.getStatus() != BillingStatus.ISSUED) {
            throw new BadRequestException(
                    "Only ISSUED payment requests can be accepted by client"
            );
        }

        // Client acceptance:
        // ISSUED -> CLIENT_ACCEPTED
        pr.setStatus(BillingStatus.CLIENT_ACCEPTED);
        milestone.setStatus(BillingStatus.CLIENT_ACCEPTED);

        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());

        recordEvent(
                pr,
                "APPROVED",
                "CLIENT",
                principal.getAccountId(),
                null
        );

        paymentRequestRepository.save(pr);
        milestoneRepository.save(milestone);

        return toPaymentResponse(pr, milestone);
    }

    @Transactional
    public PaymentRequestResponse reject(UUID paymentRequestUuid, PaymentRejectRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        String rejectStep = "CLIENT";
        if (pr.getStatus() == BillingStatus.PENDING_PM) {
            requireProjectManagerRole(principal);
            rejectStep = "PM";
        } else if (pr.getStatus() == BillingStatus.PENDING_DIRECTOR) {
            requireDirectorRole(principal);
            rejectStep = "DIRECTOR";
        } else if (pr.getStatus() == BillingStatus.ISSUED) {
            rejectStep = "CLIENT";
        } else {
            throw new BadRequestException("Only pending payment requests can be rejected");
        }
        pr.setStatus(BillingStatus.DRAFT);
        pr.setNotes(appendReason(pr.getNotes(), request.getReason().trim()));
        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.DRAFT);
        milestoneRepository.save(milestone);
        PaymentRequest saved = paymentRequestRepository.save(pr);
        recordEvent(saved, "REJECTED", rejectStep, principal.getAccountId(), request.getReason().trim());
        return toPaymentResponse(saved, milestone);
    }

    @Transactional
    public PaymentRequestResponse markPaid(UUID paymentRequestUuid) {
        AuthPrincipal principal = requireFinanceOrAdmin();
        PaymentRequest pr = requirePayment(paymentRequestUuid);
        if (pr.getStatus() != BillingStatus.CLIENT_ACCEPTED && pr.getStatus() != BillingStatus.PART_PAID) {
            throw new BadRequestException("Only CLIENT_ACCEPTED or PART_PAID payment requests can be marked paid");
        }
        pr.setStatus(BillingStatus.PAID);
        pr.setDecidedBy(principal.getAccountId());
        pr.setDecidedAt(OffsetDateTime.now());
        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        milestone.setStatus(BillingStatus.PAID);
        milestoneRepository.save(milestone);
        PaymentRequest saved = paymentRequestRepository.save(pr);
        recordEvent(saved, "PAID", "FINANCE", principal.getAccountId(), "Marked paid");
        return toPaymentResponse(saved, milestone);
    }

    @Transactional
    public PaymentRequestResponse sendReminder(UUID paymentRequestUuid) {
        AuthPrincipal principal = requireStaff();
        PaymentRequest pr = requirePayment(paymentRequestUuid);

        if (pr.getReminderSentAt() != null) {
            throw new BadRequestException("Payment reminder email has already been sent for this payment request.");
        }

        if (pr.getStatus() != BillingStatus.CLIENT_ACCEPTED) {
            throw new BadRequestException("Payment reminder can only be sent for CLIENT_ACCEPTED payment requests.");
        }

        BillingMilestone milestone = requireMilestoneByUuid(pr.getMilestoneUuid());
        if (milestone.getStatus() != BillingStatus.CLIENT_ACCEPTED) {
            throw new BadRequestException("Payment milestone is not in CLIENT_ACCEPTED state.");
        }

        if (milestone.getDueDate() == null) {
            throw new BadRequestException("Payment milestone has no due date.");
        }

        LocalDate reminderDate = milestone.getDueDate().minusDays(3);
        if (!LocalDate.now().equals(reminderDate)) {
            throw new BadRequestException("Payment request is not yet eligible for a reminder email.");
        }

        Project project = requireProject(milestone.getProjectId());

        BillingPaymentEmailService.SendResult emailResult =
                billingPaymentEmailService.notifyClient(project, milestone, pr);

        if (!emailResult.sent()) {
            throw new BadRequestException("Failed to send reminder email to client: " + (emailResult.clientEmail() != null ? emailResult.clientEmail() : "No email address found"));
        }

        pr.setReminderSentAt(OffsetDateTime.now());
        paymentRequestRepository.save(pr);
        recordEvent(pr, "REMINDER_SENT", "FINANCE", principal.getAccountId(), "Sent 3-day payment reminder email to client once.");

        return toPaymentResponse(pr, milestone, emailResult.sent(), emailResult.clientEmail());
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void processAutomated3DayPaymentReminders() {
        List<PaymentRequest> eligibleRequests = paymentRequestRepository
                .findByStatusAndReminderSentAtIsNull(BillingStatus.CLIENT_ACCEPTED);

        for (PaymentRequest pr : eligibleRequests) {
            if (activeReminderProcessing.add(pr.getUuid())) {
                try {
                    processSinglePaymentReminder(pr.getUuid());
                } catch (Exception ignored) {
                } finally {
                    activeReminderProcessing.remove(pr.getUuid());
                }
            }
        }
    }

    @Transactional
    public boolean processSinglePaymentReminder(UUID paymentRequestUuid) {
        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestUuid).orElse(null);
        if (pr == null || pr.getReminderSentAt() != null || pr.getStatus() != BillingStatus.CLIENT_ACCEPTED) {
            return false;
        }

        BillingMilestone milestone = milestoneRepository
                .findByUuidAndCompanyId(pr.getMilestoneUuid(), pr.getCompanyId())
                .orElse(null);

        if (milestone == null || milestone.getDueDate() == null || milestone.getStatus() != BillingStatus.CLIENT_ACCEPTED) {
            return false;
        }

        LocalDate reminderDate = milestone.getDueDate().minusDays(3);
        if (!LocalDate.now().equals(reminderDate)) {
            return false;
        }

        Project project = projectService.getById(milestone.getProjectId());
        if (project == null) {
            return false;
        }

        BillingPaymentEmailService.SendResult result = billingPaymentEmailService.notifyClient(project, milestone, pr);

        if (result.sent()) {
            pr.setReminderSentAt(OffsetDateTime.now());
            paymentRequestRepository.save(pr);
            recordEvent(pr, "REMINDER_SENT", "SYSTEM", null, "Automated 3-day payment reminder email sent once.");
            return true;
        }
        return false;
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
            recordEvent(pr, "SUBMITTED", "FINANCE", null, pr.getNotes());
        }
    }

    private UUID parseOptionalUuid(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(fieldName + " must be a valid UUID");
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

    private BillingMilestoneResponse toMilestoneResponseWithPayment(BillingMilestone m) {
        BillingMilestoneResponse response = toMilestoneResponse(m);
        paymentRequestRepository
                .findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(m.getUuid(), m.getCompanyId())
                .stream()
                .findFirst()
                .ifPresent(pr -> response.setLatestPaymentRequest(toPaymentResponse(pr, m)));
        return response;
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

    private PaymentRequestResponse toPaymentResponse(PaymentRequest pr, BillingMilestone milestone) {
        return toPaymentResponse(pr, milestone, null, null);
    }

    private PaymentRequestResponse toPaymentResponse(
            PaymentRequest pr, BillingMilestone milestone, Boolean clientEmailSent, String clientEmail) {
        Account requester = pr.getRequestedBy() != null
                ? accountRepository.findById(pr.getRequestedBy()).orElse(null)
                : null;
        String projectName = null;
        if (pr.getProjectId() != null) {
            try {
                projectName = requireProject(pr.getProjectId()).getName();
            } catch (RuntimeException ignored) {
                projectName = null;
            }
        }
        return PaymentRequestResponse.builder()
                .uuid(pr.getUuid())
                .milestoneUuid(pr.getMilestoneUuid())
                .projectId(pr.getProjectId())
                .companyId(pr.getCompanyId())
                .amount(pr.getAmount())
                .status(pr.getStatus())
                .notes(pr.getNotes())
                .requestedBy(pr.getRequestedBy())
                .requestedByName(requester != null ? requester.getFullName() : null)
                .requestedByEmail(requester != null ? requester.getEmail() : null)
                .decidedBy(pr.getDecidedBy())
                .decidedAt(pr.getDecidedAt())
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .milestoneName(milestone != null ? milestone.getName() : null)
                .projectName(projectName)
                .dueDate(milestone != null ? milestone.getDueDate() : null)
                .clientEmailSent(clientEmailSent)
                .clientEmail(clientEmail)
                .approvalLog(List.of())
                .build();
    }

    private void recordEvent(PaymentRequest pr, String action, String step, Long actorId, String comments) {
        if (pr == null || pr.getUuid() == null) {
            return;
        }
        try {
            billingApprovalEventService.record(pr.getUuid(), pr.getCompanyId(), action, step, actorId, comments);
        } catch (RuntimeException ignored) {
            // History is optional — never roll back Finance/PM/Director approval.
        }
    }

    private EnumSet<BillingStatus> inboxStatusesFor(AuthPrincipal principal) {
        if (principal.getRoles() == null) {
            return EnumSet.noneOf(BillingStatus.class);
        }
        boolean staff = principal.getRoles().contains(Role.FINANCE)
                || principal.getRoles().contains(Role.PROJECT_MANAGER)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN);
        if (!staff) {
            return EnumSet.noneOf(BillingStatus.class);
        }
        return EnumSet.of(
                BillingStatus.PENDING_PM,
                BillingStatus.PENDING_DIRECTOR,
                BillingStatus.ISSUED,
                BillingStatus.PAID,
                BillingStatus.PART_PAID);
    }

    private String appendComment(String notes, String comment) {
        if (!StringUtils.hasText(notes)) {
            return comment;
        }
        return notes + "\n" + comment;
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

    private AuthPrincipal requireFinanceOrAdmin() {
        AuthPrincipal principal = requireStaff();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Finance access required");
        }
        boolean allowed = principal.getRoles().contains(Role.FINANCE)
                || principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN);
        if (!allowed) {
            throw new ForbiddenException("Finance access required");
        }
        return principal;
    }

    private AuthPrincipal requireProjectManagerRole(AuthPrincipal principal) {
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Project manager access required");
        }
        boolean allowed = principal.getRoles().contains(Role.PROJECT_MANAGER)
                || principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN);
        if (!allowed) {
            throw new ForbiddenException("Project manager access required");
        }
        return principal;
    }

    private AuthPrincipal requireDirectorRole(AuthPrincipal principal) {
        if (principal.getRoles() == null) {
            throw new ForbiddenException("Director access required");
        }
        boolean allowed = principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN);
        if (!allowed) {
            throw new ForbiddenException("Director access required");
        }
        return principal;
    }
}
