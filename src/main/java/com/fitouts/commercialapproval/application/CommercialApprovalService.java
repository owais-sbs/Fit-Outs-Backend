package com.fitouts.commercialapproval.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.commercialapproval.api.ApprovalInboxItem;
import com.fitouts.commercialapproval.api.ApprovalRunResponse;
import com.fitouts.commercialapproval.api.MatrixResponse;
import com.fitouts.commercialapproval.api.MatrixUpsertRequest;
import com.fitouts.commercialapproval.api.TaskDecisionRequest;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.commercialapproval.domain.ApprovalStepMode;
import com.fitouts.commercialapproval.domain.CommercialApprovalBand;
import com.fitouts.commercialapproval.domain.CommercialApprovalBandRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalEvent;
import com.fitouts.commercialapproval.domain.CommercialApprovalEventRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalMatrix;
import com.fitouts.commercialapproval.domain.CommercialApprovalMatrixRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunStatus;
import com.fitouts.commercialapproval.domain.CommercialApprovalStep;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRole;
import com.fitouts.commercialapproval.domain.CommercialApprovalStepRoleRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalTask;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskRepository;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CommercialApprovalService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.ADMIN, Role.SUPER_ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER,
            Role.QS, Role.SENIOR_QS, Role.FINANCE);

    private static final Set<Role> MATRIX_ADMIN_ROLES = EnumSet.of(
            Role.ADMIN, Role.SUPER_ADMIN, Role.BUSINESS_OWNER);

    private final CommercialApprovalMatrixRepository matrixRepository;
    private final CommercialApprovalBandRepository bandRepository;
    private final CommercialApprovalStepRepository stepRepository;
    private final CommercialApprovalStepRoleRepository stepRoleRepository;
    private final CommercialApprovalRunRepository runRepository;
    private final CommercialApprovalTaskRepository taskRepository;
    private final CommercialApprovalEventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;
    private final List<CommercialApprovalCompletionHandler> completionHandlers;
    private final CommercialLifecycleService commercialLifecycleService;

    public CommercialApprovalService(
            CommercialApprovalMatrixRepository matrixRepository,
            CommercialApprovalBandRepository bandRepository,
            CommercialApprovalStepRepository stepRepository,
            CommercialApprovalStepRoleRepository stepRoleRepository,
            CommercialApprovalRunRepository runRepository,
            CommercialApprovalTaskRepository taskRepository,
            CommercialApprovalEventRepository eventRepository,
            AccountRepository accountRepository,
            NotificationService notificationService,
            @Lazy List<CommercialApprovalCompletionHandler> completionHandlers,
            @Lazy CommercialLifecycleService commercialLifecycleService) {
        this.matrixRepository = matrixRepository;
        this.bandRepository = bandRepository;
        this.stepRepository = stepRepository;
        this.stepRoleRepository = stepRoleRepository;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
        this.completionHandlers = completionHandlers != null ? completionHandlers : List.of();
        this.commercialLifecycleService = commercialLifecycleService;
    }

    // ── Matrix config ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MatrixResponse> listMatrices() {
        requireStaff();
        UUID companyId = requireCompany();
        ensureDefaultVariationMatrix(companyId);
        return matrixRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toMatrixResponse)
                .toList();
    }

    @Transactional
    public MatrixResponse upsertMatrix(MatrixUpsertRequest request) {
        requireMatrixAdmin();
        UUID companyId = requireCompany();
        if (request == null || request.getEventType() == null || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("eventType and name are required");
        }
        CommercialApprovalMatrix matrix = matrixRepository
                .findFirstByCompanyIdAndEventTypeAndActiveTrueOrderByCreatedAtDesc(companyId, request.getEventType())
                .orElseGet(CommercialApprovalMatrix::new);
        if (matrix.getUuid() == null) {
            matrix.setCompanyId(companyId);
            matrix.setEventType(request.getEventType());
        }
        matrix.setName(request.getName().trim());
        matrix.setActive(request.getActive() == null || request.getActive());
        matrix = matrixRepository.save(matrix);

        // Replace bands/steps
        List<CommercialApprovalBand> existingBands = bandRepository
                .findByMatrixUuidOrderBySortOrderAscMinAmountAsc(matrix.getUuid());
        for (CommercialApprovalBand band : existingBands) {
            for (CommercialApprovalStep step : stepRepository.findByBandUuidOrderByStepOrderAsc(band.getUuid())) {
                stepRoleRepository.deleteByStepUuid(step.getUuid());
            }
            stepRepository.deleteByBandUuid(band.getUuid());
        }
        bandRepository.deleteByMatrixUuid(matrix.getUuid());

        if (request.getBands() != null) {
            int bandOrder = 0;
            for (MatrixUpsertRequest.BandRequest br : request.getBands()) {
                CommercialApprovalBand band = new CommercialApprovalBand();
                band.setMatrixUuid(matrix.getUuid());
                band.setMinAmount(br.getMinAmount() != null ? br.getMinAmount() : BigDecimal.ZERO);
                band.setMaxAmount(br.getMaxAmount());
                band.setSortOrder(br.getSortOrder() != null ? br.getSortOrder() : bandOrder++);
                band = bandRepository.save(band);
                if (br.getSteps() != null) {
                    int stepOrder = 1;
                    for (MatrixUpsertRequest.StepRequest sr : br.getSteps()) {
                        CommercialApprovalStep step = new CommercialApprovalStep();
                        step.setBandUuid(band.getUuid());
                        step.setStepOrder(sr.getStepOrder() != null ? sr.getStepOrder() : stepOrder++);
                        step.setMode(sr.getMode() != null ? sr.getMode() : ApprovalStepMode.SEQUENTIAL);
                        step.setSlaHours(sr.getSlaHours() != null ? sr.getSlaHours() : 48);
                        step.setEscalateToRole(sr.getEscalateToRole());
                        step = stepRepository.save(step);
                        if (sr.getRoles() != null) {
                            for (Role role : sr.getRoles()) {
                                CommercialApprovalStepRole srRole = new CommercialApprovalStepRole();
                                srRole.setStepUuid(step.getUuid());
                                srRole.setRole(role);
                                stepRoleRepository.save(srRole);
                            }
                        }
                    }
                }
            }
        }
        return toMatrixResponse(matrix);
    }

    @Transactional
    public void ensureDefaultVariationMatrix(UUID companyId) {
        Optional<CommercialApprovalMatrix> existing = matrixRepository
                .findFirstByCompanyIdAndEventTypeAndActiveTrueOrderByCreatedAtDesc(
                        companyId, CommercialEventType.VARIATION);
        if (existing.isPresent()) {
            return;
        }
        CommercialApprovalMatrix matrix = new CommercialApprovalMatrix();
        matrix.setCompanyId(companyId);
        matrix.setEventType(CommercialEventType.VARIATION);
        matrix.setName("Default variation approvals");
        matrix.setActive(true);
        matrix = matrixRepository.save(matrix);

        // 0–50k: PM only
        seedBand(matrix.getUuid(), BigDecimal.ZERO, new BigDecimal("50000"), 0,
                List.of(List.of(Role.PROJECT_MANAGER)));
        // 50k–200k: Senior QS → PM
        seedBand(matrix.getUuid(), new BigDecimal("50000"), new BigDecimal("200000"), 1,
                List.of(List.of(Role.SENIOR_QS), List.of(Role.PROJECT_MANAGER)));
        // 200k+: Senior QS → PM → Director
        seedBand(matrix.getUuid(), new BigDecimal("200000"), null, 2,
                List.of(List.of(Role.SENIOR_QS), List.of(Role.PROJECT_MANAGER), List.of(Role.BUSINESS_OWNER)));
    }

    private void seedBand(UUID matrixUuid, BigDecimal min, BigDecimal max, int sort,
                          List<List<Role>> sequentialRoles) {
        CommercialApprovalBand band = new CommercialApprovalBand();
        band.setMatrixUuid(matrixUuid);
        band.setMinAmount(min);
        band.setMaxAmount(max);
        band.setSortOrder(sort);
        band = bandRepository.save(band);
        int order = 1;
        for (List<Role> roles : sequentialRoles) {
            CommercialApprovalStep step = new CommercialApprovalStep();
            step.setBandUuid(band.getUuid());
            step.setStepOrder(order++);
            step.setMode(ApprovalStepMode.SEQUENTIAL);
            step.setSlaHours(48);
            step.setEscalateToRole(Role.BUSINESS_OWNER);
            step = stepRepository.save(step);
            for (Role role : roles) {
                CommercialApprovalStepRole sr = new CommercialApprovalStepRole();
                sr.setStepUuid(step.getUuid());
                sr.setRole(role);
                stepRoleRepository.save(sr);
            }
        }
    }

    // ── Runtime engine ───────────────────────────────────────────────────────

    /**
     * Starts an approval run for an entity. Returns empty if no matching matrix/band
     * (caller may auto-approve). Cancels any in-progress run for the same entity.
     */
    @Transactional
    public Optional<CommercialApprovalRun> startRun(
            CommercialEventType eventType,
            UUID entityUuid,
            Long projectId,
            BigDecimal amount,
            Long actorId) {
        UUID companyId = requireCompany();
        ensureDefaultVariationMatrix(companyId);

        // Cancel existing in-progress
        runRepository.findFirstByEventTypeAndEntityUuidAndStatusOrderByStartedAtDesc(
                        eventType, entityUuid, CommercialApprovalRunStatus.IN_PROGRESS)
                .ifPresent(run -> {
                    run.setStatus(CommercialApprovalRunStatus.CANCELLED);
                    run.setCompletedAt(OffsetDateTime.now());
                    runRepository.save(run);
                    appendEvent(run, "CANCELLED", "IN_PROGRESS", "CANCELLED", actorId, amount, "Superseded by new run");
                    for (CommercialApprovalTask t : taskRepository.findByRunUuidAndStatus(
                            run.getUuid(), CommercialApprovalTaskStatus.PENDING)) {
                        t.setStatus(CommercialApprovalTaskStatus.SKIPPED);
                        taskRepository.save(t);
                    }
                    for (CommercialApprovalTask t : taskRepository.findByRunUuidAndStatus(
                            run.getUuid(), CommercialApprovalTaskStatus.WAITING)) {
                        t.setStatus(CommercialApprovalTaskStatus.SKIPPED);
                        taskRepository.save(t);
                    }
                });

        Optional<CommercialApprovalMatrix> matrixOpt = matrixRepository
                .findFirstByCompanyIdAndEventTypeAndActiveTrueOrderByCreatedAtDesc(companyId, eventType);
        if (matrixOpt.isEmpty()) {
            return Optional.empty();
        }
        CommercialApprovalMatrix matrix = matrixOpt.get();
        BigDecimal absAmount = amount != null ? amount.abs() : BigDecimal.ZERO;
        CommercialApprovalBand band = resolveBand(matrix.getUuid(), absAmount);
        if (band == null) {
            return Optional.empty();
        }
        List<CommercialApprovalStep> steps = stepRepository.findByBandUuidOrderByStepOrderAsc(band.getUuid());
        if (steps.isEmpty()) {
            return Optional.empty();
        }

        CommercialApprovalRun run = new CommercialApprovalRun();
        run.setCompanyId(companyId);
        run.setProjectId(projectId);
        run.setEventType(eventType);
        run.setEntityUuid(entityUuid);
        run.setMatrixUuid(matrix.getUuid());
        run.setBandUuid(band.getUuid());
        run.setAmount(absAmount);
        run.setStatus(CommercialApprovalRunStatus.IN_PROGRESS);
        run.setCurrentStepOrder(steps.get(0).getStepOrder());
        run = runRepository.save(run);

        for (CommercialApprovalStep step : steps) {
            List<CommercialApprovalStepRole> roles = stepRoleRepository.findByStepUuid(step.getUuid());
            boolean firstStep = step.getStepOrder() == run.getCurrentStepOrder();
            OffsetDateTime dueAt = OffsetDateTime.now().plusHours(step.getSlaHours());
            for (CommercialApprovalStepRole sr : roles) {
                CommercialApprovalTask task = new CommercialApprovalTask();
                task.setRunUuid(run.getUuid());
                task.setStepUuid(step.getUuid());
                task.setStepOrder(step.getStepOrder());
                task.setRole(sr.getRole());
                task.setStatus(firstStep ? CommercialApprovalTaskStatus.PENDING : CommercialApprovalTaskStatus.WAITING);
                task.setDueAt(firstStep ? dueAt : null);
                taskRepository.save(task);
            }
        }

        appendEvent(run, "STARTED", null, "IN_PROGRESS", actorId, absAmount, "Approval run started");
        notifyPendingTasks(run);
        return Optional.of(run);
    }

    @Transactional
    public ApprovalRunResponse approveTask(UUID taskUuid, TaskDecisionRequest request) {
        AuthPrincipal principal = requireStaff();
        CommercialApprovalTask task = taskRepository.findById(taskUuid)
                .orElseThrow(() -> new NotFoundException("Approval task not found"));
        CommercialApprovalRun run = runRepository.findById(task.getRunUuid())
                .orElseThrow(() -> new NotFoundException("Approval run not found"));
        assertCompany(run.getCompanyId());
        if (run.getProjectId() != null) {
            commercialLifecycleService.assertCommercialMutable(run.getProjectId());
        }
        if (run.getStatus() != CommercialApprovalRunStatus.IN_PROGRESS) {
            throw new BadRequestException("Approval run is not in progress");
        }
        if (task.getStatus() != CommercialApprovalTaskStatus.PENDING) {
            throw new BadRequestException("Task is not pending");
        }
        if (!canActAsRole(principal, task.getRole())) {
            throw new ForbiddenException("You cannot approve as " + task.getRole());
        }

        task.setStatus(CommercialApprovalTaskStatus.APPROVED);
        task.setDecidedBy(principal.getAccountId());
        task.setDecidedAt(OffsetDateTime.now());
        task.setComment(request != null ? trimToNull(request.getComment()) : null);
        taskRepository.save(task);
        appendEvent(run, "TASK_APPROVED", "PENDING", "APPROVED", principal.getAccountId(),
                run.getAmount(), task.getRole().name());

        advanceOrComplete(run);
        return toRunResponse(run);
    }

    @Transactional
    public ApprovalRunResponse rejectTask(UUID taskUuid, TaskDecisionRequest request) {
        AuthPrincipal principal = requireStaff();
        if (request == null || !StringUtils.hasText(request.getComment())) {
            throw new BadRequestException("comment is required to reject");
        }
        CommercialApprovalTask task = taskRepository.findById(taskUuid)
                .orElseThrow(() -> new NotFoundException("Approval task not found"));
        CommercialApprovalRun run = runRepository.findById(task.getRunUuid())
                .orElseThrow(() -> new NotFoundException("Approval run not found"));
        assertCompany(run.getCompanyId());
        if (run.getProjectId() != null) {
            commercialLifecycleService.assertCommercialMutable(run.getProjectId());
        }
        if (run.getStatus() != CommercialApprovalRunStatus.IN_PROGRESS) {
            throw new BadRequestException("Approval run is not in progress");
        }
        if (task.getStatus() != CommercialApprovalTaskStatus.PENDING) {
            throw new BadRequestException("Task is not pending");
        }
        if (!canActAsRole(principal, task.getRole())) {
            throw new ForbiddenException("You cannot reject as " + task.getRole());
        }

        task.setStatus(CommercialApprovalTaskStatus.REJECTED);
        task.setDecidedBy(principal.getAccountId());
        task.setDecidedAt(OffsetDateTime.now());
        task.setComment(request.getComment().trim());
        taskRepository.save(task);

        for (CommercialApprovalTask other : taskRepository.findByRunUuidOrderByStepOrderAsc(run.getUuid())) {
            if (other.getStatus() == CommercialApprovalTaskStatus.PENDING
                    || other.getStatus() == CommercialApprovalTaskStatus.WAITING) {
                other.setStatus(CommercialApprovalTaskStatus.SKIPPED);
                taskRepository.save(other);
            }
        }

        run.setStatus(CommercialApprovalRunStatus.REJECTED);
        run.setCompletedAt(OffsetDateTime.now());
        runRepository.save(run);
        appendEvent(run, "REJECTED", "IN_PROGRESS", "REJECTED", principal.getAccountId(),
                run.getAmount(), request.getComment().trim());

        invokeHandlersRejected(run, request.getComment().trim());
        return toRunResponse(run);
    }

    private void advanceOrComplete(CommercialApprovalRun run) {
        List<CommercialApprovalTask> currentTasks = taskRepository
                .findByRunUuidAndStepOrder(run.getUuid(), run.getCurrentStepOrder());
        boolean allApproved = currentTasks.stream()
                .allMatch(t -> t.getStatus() == CommercialApprovalTaskStatus.APPROVED);
        if (!allApproved) {
            return;
        }

        List<CommercialApprovalTask> all = taskRepository.findByRunUuidOrderByStepOrderAsc(run.getUuid());
        int nextOrder = all.stream()
                .mapToInt(CommercialApprovalTask::getStepOrder)
                .filter(o -> o > run.getCurrentStepOrder())
                .min()
                .orElse(-1);

        if (nextOrder < 0) {
            run.setStatus(CommercialApprovalRunStatus.APPROVED);
            run.setCompletedAt(OffsetDateTime.now());
            runRepository.save(run);
            appendEvent(run, "APPROVED", "IN_PROGRESS", "APPROVED", null, run.getAmount(), "All steps complete");
            invokeHandlersApproved(run);
            return;
        }

        run.setCurrentStepOrder(nextOrder);
        runRepository.save(run);

        CommercialApprovalStep stepMeta = null;
        for (CommercialApprovalTask t : all) {
            if (t.getStepOrder() == nextOrder) {
                if (stepMeta == null && t.getStepUuid() != null) {
                    stepMeta = stepRepository.findById(t.getStepUuid()).orElse(null);
                }
                t.setStatus(CommercialApprovalTaskStatus.PENDING);
                int sla = stepMeta != null ? stepMeta.getSlaHours() : 48;
                t.setDueAt(OffsetDateTime.now().plusHours(sla));
                taskRepository.save(t);
            }
        }
        appendEvent(run, "STEP_ADVANCED", null, "IN_PROGRESS", null, run.getAmount(),
                "Advanced to step " + nextOrder);
        notifyPendingTasks(run);
    }

    @Transactional(readOnly = true)
    public Optional<CommercialApprovalRun> findActiveRun(CommercialEventType eventType, UUID entityUuid) {
        return runRepository.findFirstByEventTypeAndEntityUuidAndStatusOrderByStartedAtDesc(
                eventType, entityUuid, CommercialApprovalRunStatus.IN_PROGRESS);
    }

    @Transactional(readOnly = true)
    public ApprovalRunResponse getRun(UUID runUuid) {
        requireStaff();
        return getRunInternal(runUuid);
    }

    /** Load run without auth checks — for embedding in variation responses. */
    @Transactional(readOnly = true)
    public ApprovalRunResponse getRunInternal(UUID runUuid) {
        CommercialApprovalRun run = runRepository.findById(runUuid)
                .orElseThrow(() -> new NotFoundException("Approval run not found"));
        return toRunResponse(run);
    }

    @Transactional(readOnly = true)
    public ApprovalRunResponse getRunForEntity(CommercialEventType eventType, UUID entityUuid) {
        requireStaff();
        CommercialApprovalRun run = runRepository
                .findByEventTypeAndEntityUuidOrderByStartedAtDesc(eventType, entityUuid).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No approval run for entity"));
        assertCompany(run.getCompanyId());
        return toRunResponse(run);
    }

    @Transactional(readOnly = true)
    public List<ApprovalInboxItem> inbox() {
        AuthPrincipal principal = requireStaff();
        List<Role> roles = new ArrayList<>(principal.getRoles());
        if (roles.isEmpty()) {
            return List.of();
        }
        return taskRepository.findPendingInbox(requireCompany(), roles).stream()
                .map(task -> {
                    CommercialApprovalRun run = runRepository.findById(task.getRunUuid()).orElse(null);
                    if (run == null) return null;
                    return ApprovalInboxItem.builder()
                            .taskUuid(task.getUuid())
                            .runUuid(run.getUuid())
                            .eventType(run.getEventType())
                            .entityUuid(run.getEntityUuid())
                            .projectId(run.getProjectId())
                            .amount(run.getAmount())
                            .role(task.getRole())
                            .status(task.getStatus())
                            .dueAt(task.getDueAt())
                            .title(run.getEventType() + " " + run.getEntityUuid().toString().substring(0, 8))
                            .build();
                })
                .filter(i -> i != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportCsv(OffsetDateTime from, OffsetDateTime to, CommercialEventType eventType) {
        requireMatrixAdmin();
        UUID companyId = requireCompany();
        OffsetDateTime fromTs = from != null ? from : OffsetDateTime.now().minusYears(1);
        OffsetDateTime toTs = to != null ? to : OffsetDateTime.now();
        List<CommercialApprovalEvent> events = eventRepository
                .findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtAsc(companyId, fromTs, toTs);
        StringBuilder sb = new StringBuilder();
        sb.append("created_at,run_uuid,action,from_status,to_status,actor_id,amount,detail\n");
        for (CommercialApprovalEvent e : events) {
            CommercialApprovalRun run = runRepository.findById(e.getRunUuid()).orElse(null);
            if (eventType != null && run != null && run.getEventType() != eventType) {
                continue;
            }
            sb.append(csv(e.getCreatedAt())).append(',')
                    .append(csv(e.getRunUuid())).append(',')
                    .append(csv(e.getAction())).append(',')
                    .append(csv(e.getFromStatus())).append(',')
                    .append(csv(e.getToStatus())).append(',')
                    .append(csv(e.getActorId())).append(',')
                    .append(csv(e.getAmountSnapshot())).append(',')
                    .append(csv(e.getDetail())).append('\n');
        }
        return sb.toString();
    }

    /** Daily SLA sweep: reminders then escalation. */
    @Transactional
    public int sweepRemindersAndEscalations() {
        OffsetDateTime now = OffsetDateTime.now();
        int raised = 0;
        for (CommercialApprovalTask task : taskRepository.findOverduePending(now)) {
            CommercialApprovalRun run = runRepository.findById(task.getRunUuid()).orElse(null);
            if (run == null || run.getStatus() != CommercialApprovalRunStatus.IN_PROGRESS) {
                continue;
            }
            if (task.getReminderSentAt() == null) {
                notifyRole(run, task.getRole(), "VARIATION_APPROVAL_REMINDER", "WARNING",
                        "Approval overdue",
                        "A commercial approval task is past its SLA.",
                        "/admin/variations/inbox",
                        "cam-reminder:" + task.getUuid());
                task.setReminderSentAt(now);
                taskRepository.save(task);
                raised++;
            } else if (task.getEscalatedAt() == null) {
                CommercialApprovalStep step = task.getStepUuid() != null
                        ? stepRepository.findById(task.getStepUuid()).orElse(null)
                        : null;
                Role escalateTo = step != null && step.getEscalateToRole() != null
                        ? step.getEscalateToRole()
                        : Role.BUSINESS_OWNER;
                notifyRole(run, escalateTo, "VARIATION_APPROVAL_ESCALATION", "CRITICAL",
                        "Approval escalated",
                        "Commercial approval task escalated after SLA breach.",
                        "/admin/variations/inbox",
                        "cam-escalation:" + task.getUuid());
                task.setEscalatedAt(now);
                taskRepository.save(task);
                appendEvent(run, "ESCALATED", null, "IN_PROGRESS", null, run.getAmount(),
                        "Escalated to " + escalateTo);
                raised++;
            }
        }
        return raised;
    }

    public CommercialApprovalBand resolveBand(UUID matrixUuid, BigDecimal amount) {
        List<CommercialApprovalBand> bands = bandRepository
                .findByMatrixUuidOrderBySortOrderAscMinAmountAsc(matrixUuid);
        for (CommercialApprovalBand band : bands) {
            boolean geMin = amount.compareTo(band.getMinAmount()) >= 0;
            boolean ltMax = band.getMaxAmount() == null || amount.compareTo(band.getMaxAmount()) < 0;
            if (geMin && ltMax) {
                return band;
            }
        }
        return bands.stream().max(Comparator.comparing(CommercialApprovalBand::getSortOrder)).orElse(null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void notifyPendingTasks(CommercialApprovalRun run) {
        for (CommercialApprovalTask task : taskRepository.findByRunUuidAndStatus(
                run.getUuid(), CommercialApprovalTaskStatus.PENDING)) {
            notifyRole(run, task.getRole(), "VARIATION_PENDING", "INFO",
                    "Commercial approval required",
                    run.getEventType() + " awaiting " + task.getRole() + " approval.",
                    "/admin/variations/inbox",
                    "cam-pending:" + task.getUuid());
        }
    }

    private void notifyRole(CommercialApprovalRun run, Role role, String category, String severity,
                            String title, String body, String link, String dedupeKey) {
        for (Account account : accountRepository.findAllByCompanyUuidAndRole(run.getCompanyId(), role)) {
            notificationService.raise(new NotificationService.Alert(
                    run.getCompanyId(),
                    account.getId(),
                    category,
                    severity,
                    title,
                    body,
                    link,
                    "COMMERCIAL_APPROVAL",
                    run.getUuid(),
                    dedupeKey + ":" + account.getId(),
                    false));
        }
    }

    private void invokeHandlersApproved(CommercialApprovalRun run) {
        for (CommercialApprovalCompletionHandler h : completionHandlers) {
            if (h.supports() == run.getEventType()) {
                try {
                    h.onApproved(run.getEntityUuid(), run.getUuid());
                } catch (Exception e) {
                    log.error("Completion handler failed for {}", run.getUuid(), e);
                }
            }
        }
    }

    private void invokeHandlersRejected(CommercialApprovalRun run, String comment) {
        for (CommercialApprovalCompletionHandler h : completionHandlers) {
            if (h.supports() == run.getEventType()) {
                try {
                    h.onRejected(run.getEntityUuid(), run.getUuid(), comment);
                } catch (Exception e) {
                    log.error("Completion handler failed for {}", run.getUuid(), e);
                }
            }
        }
    }

    private void appendEvent(CommercialApprovalRun run, String action, String from, String to,
                             Long actorId, BigDecimal amount, String detail) {
        CommercialApprovalEvent event = new CommercialApprovalEvent();
        event.setRunUuid(run.getUuid());
        event.setCompanyId(run.getCompanyId());
        event.setAction(action);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorId(actorId);
        event.setAmountSnapshot(amount);
        event.setDetail(detail);
        eventRepository.save(event);
    }

    private MatrixResponse toMatrixResponse(CommercialApprovalMatrix matrix) {
        List<MatrixResponse.BandResponse> bands = bandRepository
                .findByMatrixUuidOrderBySortOrderAscMinAmountAsc(matrix.getUuid()).stream()
                .map(band -> {
                    List<MatrixResponse.StepResponse> steps = stepRepository
                            .findByBandUuidOrderByStepOrderAsc(band.getUuid()).stream()
                            .map(step -> MatrixResponse.StepResponse.builder()
                                    .uuid(step.getUuid())
                                    .stepOrder(step.getStepOrder())
                                    .mode(step.getMode())
                                    .slaHours(step.getSlaHours())
                                    .escalateToRole(step.getEscalateToRole())
                                    .roles(stepRoleRepository.findByStepUuid(step.getUuid()).stream()
                                            .map(CommercialApprovalStepRole::getRole)
                                            .collect(Collectors.toList()))
                                    .build())
                            .toList();
                    return MatrixResponse.BandResponse.builder()
                            .uuid(band.getUuid())
                            .minAmount(band.getMinAmount())
                            .maxAmount(band.getMaxAmount())
                            .sortOrder(band.getSortOrder())
                            .steps(steps)
                            .build();
                })
                .toList();
        return MatrixResponse.builder()
                .uuid(matrix.getUuid())
                .eventType(matrix.getEventType())
                .name(matrix.getName())
                .active(matrix.isActive())
                .bands(bands)
                .createdAt(matrix.getCreatedAt())
                .updatedAt(matrix.getUpdatedAt())
                .build();
    }

    private ApprovalRunResponse toRunResponse(CommercialApprovalRun run) {
        List<ApprovalRunResponse.TaskResponse> tasks = taskRepository
                .findByRunUuidOrderByStepOrderAsc(run.getUuid()).stream()
                .map(t -> ApprovalRunResponse.TaskResponse.builder()
                        .uuid(t.getUuid())
                        .stepOrder(t.getStepOrder())
                        .role(t.getRole())
                        .status(t.getStatus())
                        .dueAt(t.getDueAt())
                        .decidedBy(t.getDecidedBy())
                        .decidedAt(t.getDecidedAt())
                        .comment(t.getComment())
                        .build())
                .toList();
        return ApprovalRunResponse.builder()
                .uuid(run.getUuid())
                .eventType(run.getEventType())
                .entityUuid(run.getEntityUuid())
                .projectId(run.getProjectId())
                .amount(run.getAmount())
                .status(run.getStatus())
                .currentStepOrder(run.getCurrentStepOrder())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .tasks(tasks)
                .build();
    }

    private boolean canActAsRole(AuthPrincipal principal, Role required) {
        if (principal.getRoles() == null) return false;
        if (principal.getRoles().contains(Role.ADMIN) || principal.getRoles().contains(Role.SUPER_ADMIN)) {
            return true;
        }
        return principal.getRoles().contains(required);
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal p = requirePrincipal();
        if (p.getRoles() == null || p.getRoles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw new ForbiddenException("Staff access required");
        }
        return p;
    }

    private AuthPrincipal requireMatrixAdmin() {
        AuthPrincipal p = requirePrincipal();
        if (p.getRoles() == null || p.getRoles().stream().noneMatch(MATRIX_ADMIN_ROLES::contains)) {
            throw new ForbiddenException("Matrix admin access required");
        }
        return p;
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Not authenticated");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private void assertCompany(UUID companyId) {
        if (!requireCompany().equals(companyId)) {
            throw new ForbiddenException("Wrong company");
        }
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
