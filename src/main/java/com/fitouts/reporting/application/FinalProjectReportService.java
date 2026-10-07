package com.fitouts.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.approval.api.ApprovalCaseResponse;
import com.fitouts.approval.application.ApprovalCaseService;
import com.fitouts.approval.domain.ApprovalCaseStatus;
import com.fitouts.approvalconfig.domain.ApprovalProjectNature;
import com.fitouts.approvalconfig.domain.ApprovalProjectNatureRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.billing.api.BillingMilestoneResponse;
import com.fitouts.billing.api.ClientInvoiceResponse;
import com.fitouts.billing.api.PaymentRequestResponse;
import com.fitouts.billing.application.BillingService;
import com.fitouts.billing.domain.BillingStatus;
import com.fitouts.boq.api.BoqDocumentResponse;
import com.fitouts.boq.application.BoqService;
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.application.CloseoutChecklistService;
import com.fitouts.drawing.api.ProjectDrawingResponse;
import com.fitouts.drawing.application.ProjectDrawingService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.projectdoc.api.ProjectDocumentResponse;
import com.fitouts.projectdoc.application.ProjectDocumentService;
import com.fitouts.reporting.api.FinalProjectReportResponse;
import com.fitouts.reporting.api.FinalProjectReportResponse.ActivityRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.AttentionItem;
import com.fitouts.reporting.api.FinalProjectReportResponse.AuthorityRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.BoqVersionRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.CountRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.InvoiceRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.PackageRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.RoomRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.SnagRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.TaskRow;
import com.fitouts.reporting.api.FinalProjectReportResponse.TimelineEvent;
import com.fitouts.reporting.api.FinalProjectReportResponse.VariationRow;
import com.fitouts.reporting.api.ProgressReportResponse;
import com.fitouts.roomcollab.api.ProjectRoomResponse;
import com.fitouts.roomcollab.api.RoomTaskResponse;
import com.fitouts.roomcollab.application.RoomCollabService;
import com.fitouts.roomcollab.domain.RoomTaskStatus;
import com.fitouts.schedule.api.ScheduleActivityResponse;
import com.fitouts.schedule.application.ScheduleService;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.UnauthorizedException;
import com.fitouts.snag.api.SnagResponse;
import com.fitouts.snag.application.SnagService;
import com.fitouts.snag.domain.SnagSeverity;
import com.fitouts.snag.domain.SnagStatus;
import com.fitouts.subcontractor.api.ScPaymentCertificateResponse;
import com.fitouts.subcontractor.api.ScRetentionLedgerResponse;
import com.fitouts.subcontractor.api.SubcontractorPackageResponse;
import com.fitouts.subcontractor.application.ScWave7CommercialService;
import com.fitouts.subcontractor.application.SubcontractorService;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;
import com.fitouts.validation.api.ValidationInboxResponse;
import com.fitouts.validation.application.ValidationInboxService;
import com.fitouts.variation.api.ProjectCommercialResponse;
import com.fitouts.variation.api.VariationResponse;
import com.fitouts.variation.application.VariationService;
import com.fitouts.variation.domain.VariationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinalProjectReportService {

    private static final int OPEN_TASK_LIMIT = 24;
    private static final int OPEN_SNAG_LIMIT = 15;
    private static final int DELAYED_ACTIVITY_LIMIT = 10;
    private static final int VARIATION_LIMIT = 20;
    private static final int INVOICE_LIMIT = 20;
    private static final int TIMELINE_LIMIT = 60;
    private static final int ATTENTION_LIMIT = 20;
    private static final Set<BillingStatus> INVOICED = EnumSet.of(
            BillingStatus.ISSUED, BillingStatus.CLIENT_ACCEPTED, BillingStatus.PAID, BillingStatus.PART_PAID);
    private static final Set<VariationStatus> PENDING_VARIATION = EnumSet.of(
            VariationStatus.AWAITING_TRIAGE, VariationStatus.DRAFT, VariationStatus.INTERNAL_REVIEW,
            VariationStatus.ISSUED_TO_CLIENT, VariationStatus.REVISED);
    private static final Set<String> TENDERING = Set.of("DRAFT", "ISSUED");

    private final ProjectService projectService;
    private final AccountRepository accountRepository;
    private final ApprovalProjectNatureRepository projectNatureRepository;
    private final ProgressReportService progressReportService;
    private final ScheduleService scheduleService;
    private final BoqService boqService;
    private final ApprovalCaseService approvalCaseService;
    private final RoomCollabService roomCollabService;
    private final SnagService snagService;
    private final SubcontractorService subcontractorService;
    private final BillingService billingService;
    private final ScWave7CommercialService scCommercialService;
    private final VariationService variationService;
    private final ProjectDocumentService projectDocumentService;
    private final ProjectDrawingService projectDrawingService;
    private final CloseoutChecklistService closeoutChecklistService;
    private final ValidationInboxService validationInboxService;

    public FinalProjectReportResponse build(Long projectId) {
        Project project = projectService.getById(projectId);
        boolean internal = canSeeInternalCommercial();
        LocalDate today = LocalDate.now();
        OffsetDateTime generated = OffsetDateTime.now();

        Account clientAccount = loadClient(project);
        String nature = loadNature(project);
        ProgressReportResponse progressReport = safe("progress", () -> progressReportService.getReport(projectId));
        var published = safe("programme", () -> scheduleService.getPublishedSchedule(projectId));
        List<BoqDocumentResponse> boqs = safeList("boq", () -> boqService.listByProject(projectId));
        ProjectCommercialResponse commercial = internal
                ? safe("commercial", () -> variationService.getCommercial(projectId))
                : null;
        List<ApprovalCaseResponse> approvals = safeList("approvals", () -> approvalCaseService.listForProject(projectId));
        List<ProjectRoomResponse> rooms = safeList("rooms", () -> roomCollabService.listRooms(projectId));
        List<RoomTaskResponse> tasks = safeList("tasks", () -> roomCollabService.listTasksForProject(projectId));
        List<SnagResponse> snags = loadSnags(projectId);
        List<SubcontractorPackageResponse> packages = internal
                ? safeList("packages", () -> subcontractorService.listPackages(projectId))
                : List.of();
        List<BillingMilestoneResponse> milestones = internal
                ? safeList("milestones", () -> billingService.listMilestones(projectId))
                : List.of();
        List<ClientInvoiceResponse> invoices = safeList("invoices", () -> billingService.listClientInvoices(projectId));
        List<VariationResponse> variations = safeList("variations", () -> variationService.listForProject(projectId));
        List<ProjectDocumentResponse> documents = loadDocuments(projectId, internal);
        List<ProjectDrawingResponse> drawings = safeList("drawings", () -> projectDrawingService.listByProject(projectId))
                .stream()
                .filter(d -> d.getCompanyId() == null || project.getCompanyId() == null
                        || project.getCompanyId().equals(d.getCompanyId()))
                .filter(d -> projectId.equals(d.getProjectId()))
                .toList();
        CloseoutChecklistResponse closeout = internal
                ? safe("closeout", () -> closeoutChecklistService.get(projectId))
                : null;
        ValidationInboxResponse validation = internal
                ? safe("validation", () -> validationInboxService.inboxForProject(projectId))
                : null;
        List<ScPaymentCertificateResponse> certificates = internal
                ? safeList("certificates", () -> scCommercialService.listCertificatesForProject(projectId))
                : List.of();
        List<ScRetentionLedgerResponse> retention = internal
                ? safeList("retention", () -> scCommercialService.listRetentionLedger(projectId, null))
                : List.of();

        BigDecimal contractValue = contractValue(commercial, project);
        BoqDocumentResponse approved = pickApproved(boqs);
        BigDecimal approvedAmount = approved == null ? null : approved.getGrandTotal();
        List<ScheduleActivityResponse> activities = published == null || published.getActivities() == null
                ? List.of()
                : published.getActivities().stream().filter(a -> projectId.equals(a.getProjectId())).toList();
        BigDecimal overall = overallProgress(activities, project);

        List<TimelineEvent> timeline = buildTimeline(
                project, boqs, approvals, packages, tasks, snags, variations, invoices, milestones);
        List<AttentionItem> attention = buildAttention(
                project, approvals, activities, tasks, snags, invoices, milestones, variations, today);

        String clientName = firstText(project.getClientName(), clientAccount == null ? null : clientAccount.getFullName());

        return FinalProjectReportResponse.builder()
                .fileName(fileName(project.getName(), today))
                .generatedAt(generated.toString())
                .internalView(internal)
                .project(FinalProjectReportResponse.ProjectHeader.builder()
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .status(project.getStatus())
                        .leadReference(blankToNull(project.getLeadReferenceNo()))
                        .manager(blankToNull(project.getAssignedManager()))
                        .projectType(blankToNull(project.getProjectType()))
                        .projectNature(nature)
                        .developer(developer(project))
                        .clientName(clientName)
                        .location(location(project))
                        .build())
                .kpis(FinalProjectReportResponse.Kpis.builder()
                        .contractValue(contractValue)
                        .approvedBoqValue(approvedAmount)
                        .overallProgress(overall)
                        .startDate(project.getStartDate() == null ? null : project.getStartDate().toString())
                        .targetCompletion(project.getExpectedCompletionDate() == null
                                ? null : project.getExpectedCompletionDate().toString())
                        .projectStatus(blankToNull(project.getStatus()))
                        .build())
                .progress(FinalProjectReportResponse.ProgressBlock.builder()
                        .actualPercent(overall)
                        .source(progressSource(activities, project))
                        .build())
                .boq(buildBoq(boqs, approved, contractValue))
                .authorities(buildAuthorities(approvals, today))
                .programme(buildProgramme(progressReport, activities, today))
                .rooms(buildRooms(rooms, tasks))
                .tasks(buildTasks(tasks, today))
                .snags(buildSnags(snags))
                .subcontractors(buildPackages(packages))
                .billing(buildBilling(milestones, invoices))
                .scCommercial(internal ? buildScCommercial(certificates, retention) : null)
                .variations(buildVariations(variations))
                .documents(buildDocuments(documents, drawings, closeout, validation))
                .timeline(timeline)
                .attention(attention)
                .client(buildClient(project, clientAccount, clientName))
                .build();
    }

    static String fileName(String projectName, LocalDate day) {
        String raw = StringUtils.hasText(projectName) ? projectName.trim() : "Project";
        String safe = raw.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(safe)) {
            safe = "Project";
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80).replaceAll("_+$", "");
        }
        return safe + "_Final_Project_Report_" + day + ".pdf";
    }

    private FinalProjectReportResponse.BoqBlock buildBoq(
            List<BoqDocumentResponse> boqs, BoqDocumentResponse approved, BigDecimal contractValue) {
        List<BoqVersionRow> versions = boqs.stream()
                .filter(d -> d.getStatus() != BoqDocumentStatus.OBSOLETE)
                .sorted(Comparator.comparing(this::boqSortDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> BoqVersionRow.builder()
                        .version(firstText(d.getVersion(), d.getRevisionLabel(), "BOQ"))
                        .status(d.getStatus() == null ? null : d.getStatus().name())
                        .date(boqDate(d))
                        .amount(d.getGrandTotal())
                        .build())
                .toList();
        BigDecimal difference = null;
        if (contractValue != null && approved != null && approved.getGrandTotal() != null) {
            difference = contractValue.subtract(approved.getGrandTotal());
        }
        return FinalProjectReportResponse.BoqBlock.builder()
                .hasApproved(approved != null)
                .version(approved == null ? null : firstText(approved.getVersion(), approved.getRevisionLabel()))
                .status(approved == null || approved.getStatus() == null ? null : approved.getStatus().name())
                .reference(approved == null ? null : firstText(approved.getRevisionLabel(), approved.getVersion()))
                .lineCount(approved == null || approved.getLines() == null ? null : approved.getLines().size())
                .approvedDate(approved == null || approved.getApprovedAt() == null
                        ? null : approved.getApprovedAt().toLocalDate().toString())
                .approvedAmount(approved == null ? null : approved.getGrandTotal())
                .contractValue(contractValue)
                .difference(difference)
                .versions(versions)
                .build();
    }

    private FinalProjectReportResponse.AuthorityBlock buildAuthorities(
            List<ApprovalCaseResponse> approvals, LocalDate today) {
        int live = 0;
        int blocked = 0;
        int awaiting = 0;
        int expiring = 0;
        List<AuthorityRow> blockers = new ArrayList<>();
        for (ApprovalCaseResponse item : approvals) {
            ApprovalCaseStatus status = parseStatus(item.getStatus());
            boolean isLive = status != null && status.isActivePermit();
            boolean isAwaiting = status != null && status.isAwaitingAuthority();
            boolean isExpiring = status == ApprovalCaseStatus.EXPIRING_SOON
                    || (item.getDaysToExpiry() != null && item.getDaysToExpiry() >= 0 && item.getDaysToExpiry() <= 30
                            && status != null && status.isApprovedOrLater());
            boolean isBlocked = status == ApprovalCaseStatus.EXPIRED
                    || status == ApprovalCaseStatus.REJECTED
                    || status == ApprovalCaseStatus.COMMENTS_RECEIVED
                    || item.getBlockingChecklistCount() > 0
                    || StringUtils.hasText(item.getBlockReason())
                    || (item.getUnmetPrerequisites() != null && !item.getUnmetPrerequisites().isEmpty());
            if (isLive) live++;
            if (isAwaiting) awaiting++;
            if (isExpiring) expiring++;
            if (isBlocked) {
                blocked++;
                if (blockers.size() < 12) {
                    String reason = firstText(item.getBlockReason(),
                            join(item.getBlockingCompanyDocuments()),
                            join(item.getUnmetPrerequisites()));
                    String due = item.getExpiryDate() != null ? item.getExpiryDate().toString()
                            : item.getSlaDueDate() != null ? item.getSlaDueDate().toString() : null;
                    blockers.add(AuthorityRow.builder()
                            .approval(firstText(item.getPermitTypeName(), item.getCaseNumber(), item.getPermitTypeCode()))
                            .authority(firstText(item.getAuthorityName(), item.getAuthorityCode()))
                            .status(item.getStatus())
                            .reason(reason)
                            .dueDate(due)
                            .build());
                }
            }
        }
        return FinalProjectReportResponse.AuthorityBlock.builder()
                .live(live)
                .blocked(blocked)
                .awaiting(awaiting)
                .expiring(expiring)
                .blockers(blockers)
                .build();
    }

    private FinalProjectReportResponse.ProgrammeBlock buildProgramme(
            ProgressReportResponse progressReport,
            List<ScheduleActivityResponse> activities,
            LocalDate today) {
        if (activities.isEmpty()) {
            return FinalProjectReportResponse.ProgrammeBlock.builder().published(false).build();
        }
        int completed = 0;
        int inProgress = 0;
        int delayed = 0;
        LocalDate start = null;
        LocalDate end = null;
        List<ActivityRow> delayedRows = new ArrayList<>();
        for (ScheduleActivityResponse activity : activities) {
            int pct = activity.getPercentComplete();
            if (pct >= 100) completed++;
            else if (pct > 0) inProgress++;
            if (activity.getStartDate() != null && (start == null || activity.getStartDate().isBefore(start))) {
                start = activity.getStartDate();
            }
            if (activity.getEndDate() != null && (end == null || activity.getEndDate().isAfter(end))) {
                end = activity.getEndDate();
            }
            boolean isDelayed = pct < 100 && activity.getEndDate() != null && activity.getEndDate().isBefore(today);
            if (isDelayed) {
                delayed++;
                if (delayedRows.size() < DELAYED_ACTIVITY_LIMIT) {
                    long days = ChronoUnit.DAYS.between(activity.getEndDate(), today);
                    delayedRows.add(ActivityRow.builder()
                            .activity(firstText(activity.getName(), activity.getActivityCode()))
                            .plannedFinish(activity.getEndDate().toString())
                            .forecastOrActual(pct + "%")
                            .variance(days + " days past finish")
                            .owner(blankToNull(activity.getAssigneeName()))
                            .status(activity.isCritical() ? "Delayed · Critical" : "Delayed")
                            .build());
                }
            }
        }
        String baseline = progressReport == null ? null : blankToNull(progressReport.getBaselineName());
        List<CountRow> counts = new ArrayList<>();
        counts.add(count("Completed", completed));
        counts.add(count("In progress", inProgress));
        counts.add(count("Not started", Math.max(0, activities.size() - completed - inProgress)));
        return FinalProjectReportResponse.ProgrammeBlock.builder()
                .published(true)
                .baselineName(baseline)
                .startDate(start == null ? null : start.toString())
                .targetDate(end == null ? null : end.toString())
                .total(activities.size())
                .completed(completed)
                .inProgress(inProgress)
                .delayed(delayed)
                .progressPercent(weighted(activities))
                .delayedActivities(delayedRows)
                .statusCounts(counts.stream().filter(c -> c.getCount() > 0).toList())
                .build();
    }

    private List<RoomRow> buildRooms(List<ProjectRoomResponse> rooms, List<RoomTaskResponse> tasks) {
        Map<String, List<RoomTaskResponse>> byRoom = new LinkedHashMap<>();
        for (RoomTaskResponse task : tasks) {
            String key = task.getProjectRoomId() == null ? "" : task.getProjectRoomId().toString();
            byRoom.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
        }
        List<RoomRow> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ProjectRoomResponse room : rooms) {
            String key = room.getUuid() == null ? "" : room.getUuid().toString();
            seen.add(key);
            rows.add(roomRow(roomLabel(room.getFloorLabel(), room.getName()), byRoom.getOrDefault(key, List.of())));
        }
        for (RoomTaskResponse task : tasks) {
            String key = task.getProjectRoomId() == null ? "" : task.getProjectRoomId().toString();
            if (seen.add(key)) {
                rows.add(roomRow(roomLabel(task.getFloorLabel(), task.getRoomName()), byRoom.getOrDefault(key, List.of())));
            }
        }
        return rows;
    }

    private RoomRow roomRow(String name, List<RoomTaskResponse> tasks) {
        int total = tasks.size();
        int done = (int) tasks.stream().filter(this::taskDone).count();
        BigDecimal pct = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(done * 100.0 / total).setScale(0, RoundingMode.HALF_UP);
        String status = total == 0 ? "No tasks" : done == total ? "Complete" : done == 0 ? "Not started" : "In progress";
        return RoomRow.builder()
                .room(StringUtils.hasText(name) ? name : "Room")
                .completedTasks(done)
                .totalTasks(total)
                .completionPercent(pct)
                .status(status)
                .build();
    }

    private FinalProjectReportResponse.TaskBlock buildTasks(List<RoomTaskResponse> tasks, LocalDate today) {
        int completed = 0;
        int inProgress = 0;
        int overdue = 0;
        int upcoming = 0;
        List<TaskRow> open = new ArrayList<>();
        List<RoomTaskResponse> ranked = tasks.stream()
                .sorted(Comparator.comparingInt((RoomTaskResponse t) -> taskRank(t, today))
                        .thenComparing(t -> t.getClientDeadline() == null ? OffsetDateTime.MAX : t.getClientDeadline()))
                .toList();
        for (RoomTaskResponse task : tasks) {
            if (taskDone(task)) completed++;
            else if (task.getStatus() == RoomTaskStatus.AWAITING_CLIENT
                    || task.getStatus() == RoomTaskStatus.CHANGES_REQUESTED) inProgress++;
            if (isOverdue(task, today)) overdue++;
            else if (task.getStatus() == RoomTaskStatus.OPEN) upcoming++;
        }
        for (RoomTaskResponse task : ranked) {
            if (taskDone(task) || open.size() >= OPEN_TASK_LIMIT) continue;
            open.add(TaskRow.builder()
                    .task(task.getTitle())
                    .location(roomLabel(task.getFloorLabel(), task.getRoomName()))
                    .type(firstText(task.getTypeLabel(), task.getTaskType() == null ? null : task.getTaskType().name()))
                    .owner(null)
                    .dueDate(task.getClientDeadline() == null ? null : task.getClientDeadline().toLocalDate().toString())
                    .status(isOverdue(task, today) ? "OVERDUE" : task.getStatus() == null ? null : task.getStatus().name())
                    .build());
        }
        List<CountRow> counts = List.of(
                count("Completed", completed),
                count("In progress", inProgress),
                count("Overdue", overdue),
                count("Upcoming", upcoming));
        return FinalProjectReportResponse.TaskBlock.builder()
                .total(tasks.size())
                .completed(completed)
                .inProgress(inProgress)
                .overdue(overdue)
                .upcoming(upcoming)
                .statusCounts(counts.stream().filter(c -> c.getCount() > 0).toList())
                .openTasks(open)
                .build();
    }

    private FinalProjectReportResponse.SnagBlock buildSnags(List<SnagResponse> snags) {
        int open = 0;
        int inProgress = 0;
        int ready = 0;
        int resolved = 0;
        int closed = 0;
        for (SnagResponse snag : snags) {
            SnagStatus status = snag.getStatus();
            if (status == SnagStatus.OPEN) open++;
            else if (status == SnagStatus.IN_PROGRESS) inProgress++;
            else if (status == SnagStatus.READY_FOR_INSPECTION) ready++;
            else if (status == SnagStatus.RESOLVED) resolved++;
            else if (status == SnagStatus.CLOSED) closed++;
        }
        List<SnagRow> rows = snags.stream()
                .filter(s -> s.getStatus() != SnagStatus.CLOSED)
                .sorted(Comparator.comparingInt(this::severityRank)
                        .thenComparing(s -> s.getDueDate() == null ? LocalDate.MAX : s.getDueDate()))
                .limit(OPEN_SNAG_LIMIT)
                .map(s -> SnagRow.builder()
                        .snag(s.getTitle())
                        .location(firstText(s.getLocation(), s.getRoomName()))
                        .trade(firstText(join(s.getScRecipientNames()), s.getAssigneeName()))
                        .severity(s.getSeverity() == null ? null : s.getSeverity().name())
                        .dueDate(s.getDueDate() == null ? null : s.getDueDate().toString())
                        .status(s.getStatus() == null ? null : s.getStatus().name())
                        .build())
                .toList();
        List<CountRow> counts = List.of(
                count("Open", open),
                count("In progress", inProgress),
                count("Ready for inspection", ready),
                count("Resolved", resolved),
                count("Closed", closed));
        return FinalProjectReportResponse.SnagBlock.builder()
                .total(snags.size())
                .open(open)
                .inProgress(inProgress)
                .readyForInspection(ready)
                .resolved(resolved)
                .closed(closed)
                .statusCounts(counts.stream().filter(c -> c.getCount() > 0).toList())
                .openSnags(rows)
                .build();
    }

    private FinalProjectReportResponse.SubcontractBlock buildPackages(List<SubcontractorPackageResponse> packages) {
        int tendering = 0;
        int awarded = 0;
        int active = 0;
        int completed = 0;
        List<PackageRow> rows = new ArrayList<>();
        for (SubcontractorPackageResponse pkg : packages) {
            SubcontractorPackageStatus status = pkg.getStatus();
            boolean tenderOpen = status == SubcontractorPackageStatus.OPEN
                    || (pkg.getTenderStatus() != null && TENDERING.contains(pkg.getTenderStatus()));
            if (tenderOpen && status == SubcontractorPackageStatus.OPEN) tendering++;
            if (status == SubcontractorPackageStatus.APPOINTED) awarded++;
            if (status == SubcontractorPackageStatus.IN_PROGRESS) active++;
            if (status == SubcontractorPackageStatus.COMPLETE) completed++;
            if (status == SubcontractorPackageStatus.APPOINTED
                    || status == SubcontractorPackageStatus.IN_PROGRESS
                    || status == SubcontractorPackageStatus.COMPLETE) {
                rows.add(PackageRow.builder()
                        .packageName(pkg.getName())
                        .trade(firstText(pkg.getTradePackageName(), pkg.getTradePackageCode()))
                        .subcontractor(blankToNull(pkg.getAppointedCompanyName()))
                        .awardValue(pkg.getEstimatedBoqValue())
                        .progress(packageProgress(pkg))
                        .status(status == null ? null : status.name())
                        .build());
            }
        }
        List<CountRow> counts = List.of(
                count("Tendering", tendering),
                count("Awarded", awarded),
                count("Active", active),
                count("Completed", completed));
        return FinalProjectReportResponse.SubcontractBlock.builder()
                .total(packages.size())
                .tendering(tendering)
                .awarded(awarded)
                .active(active)
                .completed(completed)
                .statusCounts(counts.stream().filter(c -> c.getCount() > 0).toList())
                .packages(rows)
                .build();
    }

    private FinalProjectReportResponse.BillingBlock buildBilling(
            List<BillingMilestoneResponse> milestones, List<ClientInvoiceResponse> invoices) {
        if (!milestones.isEmpty()) {
            BigDecimal total = sum(milestones.stream().map(BillingMilestoneResponse::getAmount));
            BigDecimal invoiced = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            List<InvoiceRow> rows = new ArrayList<>();
            for (BillingMilestoneResponse milestone : milestones) {
                PaymentRequestResponse payment = milestone.getLatestPaymentRequest();
                BillingStatus status = payment != null && payment.getStatus() != null
                        ? payment.getStatus() : milestone.getStatus();
                BigDecimal amount = payment != null && payment.getAmount() != null
                        ? payment.getAmount() : milestone.getAmount();
                if (status != null && INVOICED.contains(status)) {
                    invoiced = invoiced.add(nz(amount));
                    if (status == BillingStatus.PAID) {
                        received = received.add(nz(amount));
                    }
                }
                if (rows.size() < INVOICE_LIMIT && status != null && INVOICED.contains(status)) {
                    BigDecimal paid = status == BillingStatus.PAID ? nz(amount) : BigDecimal.ZERO;
                    rows.add(InvoiceRow.builder()
                            .invoice(firstText(milestone.getName(), "Milestone"))
                            .amount(amount)
                            .issueDate(payment == null || payment.getCreatedAt() == null
                                    ? null : payment.getCreatedAt().toLocalDate().toString())
                            .dueDate(milestone.getDueDate() == null ? null : milestone.getDueDate().toString())
                            .paid(paid)
                            .outstanding(nz(amount).subtract(paid))
                            .status(status.name())
                            .build());
                }
            }
            return billingBlock(true, total, invoiced, received, rows);
        }
        if (invoices.isEmpty()) {
            return FinalProjectReportResponse.BillingBlock.builder().hasRecords(false).build();
        }
        BigDecimal invoiced = sum(invoices.stream().map(ClientInvoiceResponse::getAmount));
        BigDecimal received = sum(invoices.stream()
                .filter(i -> i.getStatus() == BillingStatus.PAID)
                .map(ClientInvoiceResponse::getAmount));
        List<InvoiceRow> rows = invoices.stream().limit(INVOICE_LIMIT).map(inv -> {
            BigDecimal paid = inv.getStatus() == BillingStatus.PAID ? nz(inv.getAmount()) : BigDecimal.ZERO;
            return InvoiceRow.builder()
                    .invoice(firstText(inv.getMilestoneName(), "Invoice"))
                    .amount(inv.getAmount())
                    .issueDate(inv.getIssuedAt() == null ? null : inv.getIssuedAt().toLocalDate().toString())
                    .paid(paid)
                    .outstanding(nz(inv.getAmount()).subtract(paid))
                    .status(inv.getStatus() == null ? null : inv.getStatus().name())
                    .build();
        }).toList();
        return billingBlock(true, invoiced, invoiced, received, rows);
    }

    private FinalProjectReportResponse.BillingBlock billingBlock(
            boolean has, BigDecimal total, BigDecimal invoiced, BigDecimal received, List<InvoiceRow> rows) {
        BigDecimal outstanding = nz(invoiced).subtract(nz(received));
        BigDecimal collection = invoiced != null && invoiced.signum() > 0
                ? nz(received).multiply(BigDecimal.valueOf(100)).divide(invoiced, 1, RoundingMode.HALF_UP)
                : null;
        return FinalProjectReportResponse.BillingBlock.builder()
                .hasRecords(has)
                .total(total)
                .invoiced(invoiced)
                .received(received)
                .outstanding(outstanding)
                .collectionPercent(collection)
                .invoices(rows)
                .build();
    }

    private FinalProjectReportResponse.ScCommercialBlock buildScCommercial(
            List<ScPaymentCertificateResponse> certificates, List<ScRetentionLedgerResponse> retention) {
        if (certificates.isEmpty() && retention.isEmpty()) {
            return FinalProjectReportResponse.ScCommercialBlock.builder().hasRecords(false).build();
        }
        BigDecimal certified = sum(certificates.stream().map(ScPaymentCertificateResponse::getCertifiedValue));
        BigDecimal payable = sum(certificates.stream().map(ScPaymentCertificateResponse::getNetPayable));
        BigDecimal paid = sum(certificates.stream().map(ScPaymentCertificateResponse::getPaidAmount));
        BigDecimal held = sum(retention.stream().map(ScRetentionLedgerResponse::getOutstandingBalance));
        return FinalProjectReportResponse.ScCommercialBlock.builder()
                .hasRecords(true)
                .certified(certified)
                .payable(payable)
                .paid(paid)
                .retentionHeld(held)
                .outstandingLiability(nz(payable).subtract(nz(paid)))
                .build();
    }

    private FinalProjectReportResponse.VariationBlock buildVariations(List<VariationResponse> variations) {
        int approved = 0;
        int pending = 0;
        int rejected = 0;
        BigDecimal approvedValue = BigDecimal.ZERO;
        BigDecimal pendingValue = BigDecimal.ZERO;
        for (VariationResponse item : variations) {
            if (item.getStatus() == VariationStatus.APPROVED) {
                approved++;
                approvedValue = approvedValue.add(nz(item.getSellDelta()));
            } else if (item.getStatus() == VariationStatus.REJECTED) {
                rejected++;
            } else if (item.getStatus() != null && PENDING_VARIATION.contains(item.getStatus())) {
                pending++;
                pendingValue = pendingValue.add(nz(item.getSellDelta()));
            }
        }
        List<VariationRow> rows = variations.stream()
                .sorted(Comparator.comparingInt(v -> v.getStatus() == VariationStatus.APPROVED
                        || v.getStatus() == VariationStatus.REJECTED ? 1 : 0))
                .limit(VARIATION_LIMIT)
                .map(v -> VariationRow.builder()
                        .variation(firstText(v.getCrNumber(), v.getTitle()))
                        .description(firstText(v.getTitle(), v.getDescription()))
                        .costImpact(v.getSellDelta())
                        .scheduleImpact(v.getProposedDelayDays() == null ? null : v.getProposedDelayDays() + " days")
                        .status(v.getStatus() == null ? null : v.getStatus().name())
                        .build())
                .toList();
        return FinalProjectReportResponse.VariationBlock.builder()
                .total(variations.size())
                .approved(approved)
                .pending(pending)
                .rejected(rejected)
                .approvedValue(variations.isEmpty() ? null : approvedValue)
                .pendingValue(variations.isEmpty() ? null : pendingValue)
                .rows(rows)
                .build();
    }

    private List<CountRow> buildDocuments(
            List<ProjectDocumentResponse> documents,
            List<ProjectDrawingResponse> drawings,
            CloseoutChecklistResponse closeout,
            ValidationInboxResponse validation) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!drawings.isEmpty()) {
            counts.put("Drawings", drawings.size());
        }
        for (ProjectDocumentResponse doc : documents) {
            String bucket = documentBucket(doc.getCategory(), doc.getSourceType());
            counts.merge(bucket, 1, Integer::sum);
        }
        if (closeout != null && closeout.getItems() != null) {
            int done = closeout.getSatisfiedCount();
            int open = closeout.getOutstandingCount();
            if (done + open > 0) {
                counts.put("Completion checklist (" + done + " satisfied, " + open + " open)", 0);
            }
        }
        if (validation != null) {
            int pending = validation.getPendingProgressCount()
                    + validation.getPendingClaimCount()
                    + validation.getPendingVariationCount()
                    + validation.getPendingInvoiceCount()
                    + validation.getPendingSiteReportCount();
            if (pending > 0) {
                counts.put("Validation pending", pending);
            }
        }
        List<CountRow> rows = new ArrayList<>();
        counts.forEach((label, count) -> {
            if (label.startsWith("Completion checklist")) {
                rows.add(CountRow.builder().label(label).count(closeout == null ? 0 : closeout.getItems().size()).build());
            } else if (count > 0) {
                rows.add(count(label, count));
            }
        });
        return rows;
    }

    private List<TimelineEvent> buildTimeline(
            Project project,
            List<BoqDocumentResponse> boqs,
            List<ApprovalCaseResponse> approvals,
            List<SubcontractorPackageResponse> packages,
            List<RoomTaskResponse> tasks,
            List<SnagResponse> snags,
            List<VariationResponse> variations,
            List<ClientInvoiceResponse> invoices,
            List<BillingMilestoneResponse> milestones) {
        List<TimelineEvent> events = new ArrayList<>();
        if (project.getCreatedAt() != null) {
            events.add(event(project.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                    "Project created", project.getName()));
        }
        for (BoqDocumentResponse boq : boqs) {
            if (boq.getStatus() == BoqDocumentStatus.APPROVED || boq.getStatus() == BoqDocumentStatus.FINAL) {
                if (boq.getApprovedAt() != null) {
                    events.add(event(boq.getApprovedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                            "BOQ approved", firstText(boq.getVersion(), boq.getRevisionLabel(), "BOQ")
                                    + (boq.getGrandTotal() == null ? "" : "")));
                }
            }
        }
        for (ApprovalCaseResponse item : approvals) {
            String name = firstText(item.getPermitTypeName(), item.getCaseNumber());
            if (item.getSubmittedDate() != null) {
                events.add(event(item.getSubmittedDate().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(),
                        "Authority application submitted", name));
            }
            if (item.getApprovedDate() != null) {
                events.add(event(item.getApprovedDate().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(),
                        "Authority approval granted", name));
            }
        }
        packages.stream()
                .filter(p -> p.getCreatedAt() != null)
                .sorted(Comparator.comparing(SubcontractorPackageResponse::getCreatedAt))
                .limit(12)
                .forEach(p -> events.add(event(p.getCreatedAt(), "Subcontract package recorded", p.getName())));
        tasks.stream()
                .filter(t -> t.getApprovedAt() != null)
                .sorted(Comparator.comparing(RoomTaskResponse::getApprovedAt).reversed())
                .limit(12)
                .forEach(t -> events.add(event(t.getApprovedAt(), "Task approved", t.getTitle())));
        snags.stream()
                .filter(s -> s.getCreatedAt() != null)
                .sorted(Comparator.comparing(SnagResponse::getCreatedAt).reversed())
                .limit(12)
                .forEach(s -> events.add(event(s.getCreatedAt(), "Snag raised", s.getTitle())));
        snags.stream()
                .filter(s -> s.getStatus() == SnagStatus.CLOSED && s.getUpdatedAt() != null)
                .sorted(Comparator.comparing(SnagResponse::getUpdatedAt).reversed())
                .limit(8)
                .forEach(s -> events.add(event(s.getUpdatedAt(), "Snag closed", s.getTitle())));
        for (VariationResponse variation : variations) {
            if (variation.getApprovedAt() != null) {
                events.add(event(variation.getApprovedAt(), "Variation approved",
                        firstText(variation.getCrNumber(), variation.getTitle())));
            } else if (variation.getCreatedAt() != null) {
                events.add(event(variation.getCreatedAt(), "Variation recorded",
                        firstText(variation.getCrNumber(), variation.getTitle())));
            }
        }
        for (ClientInvoiceResponse invoice : invoices) {
            if (invoice.getIssuedAt() != null) {
                events.add(event(invoice.getIssuedAt(), "Invoice issued", firstText(invoice.getMilestoneName(), "Invoice")));
            }
            if (invoice.getStatus() == BillingStatus.PAID && invoice.getUpdatedAt() != null) {
                events.add(event(invoice.getUpdatedAt(), "Payment marked paid",
                        firstText(invoice.getMilestoneName(), "Invoice")));
            }
        }
        for (BillingMilestoneResponse milestone : milestones) {
            PaymentRequestResponse payment = milestone.getLatestPaymentRequest();
            if (payment != null && payment.getStatus() == BillingStatus.PAID && payment.getUpdatedAt() != null
                    && invoices.stream().noneMatch(i -> payment.getUuid().equals(i.getPaymentRequestUuid()))) {
                events.add(event(payment.getUpdatedAt(), "Payment marked paid", milestone.getName()));
            }
        }
        return events.stream()
                .filter(e -> StringUtils.hasText(e.getAt()))
                .sorted(Comparator.comparing(TimelineEvent::getAt))
                .limit(TIMELINE_LIMIT)
                .toList();
    }

    private List<AttentionItem> buildAttention(
            Project project,
            List<ApprovalCaseResponse> approvals,
            List<ScheduleActivityResponse> activities,
            List<RoomTaskResponse> tasks,
            List<SnagResponse> snags,
            List<ClientInvoiceResponse> invoices,
            List<BillingMilestoneResponse> milestones,
            List<VariationResponse> variations,
            LocalDate today) {
        List<AttentionItem> items = new ArrayList<>();
        if (project.getClientId() == null && !StringUtils.hasText(project.getClientName())) {
            items.add(attention("Project", "Client is not assigned", "MEDIUM", project.getAssignedManager(),
                    "Assign a client on the project"));
        }
        if (activities.isEmpty()) {
            items.add(attention("Programme", "Programme not published yet", "MEDIUM", project.getAssignedManager(),
                    "Publish the programme"));
        }
        for (ApprovalCaseResponse item : approvals) {
            if (items.size() >= ATTENTION_LIMIT) break;
            ApprovalCaseStatus status = parseStatus(item.getStatus());
            boolean blocked = status == ApprovalCaseStatus.EXPIRED
                    || status == ApprovalCaseStatus.REJECTED
                    || status == ApprovalCaseStatus.COMMENTS_RECEIVED
                    || StringUtils.hasText(item.getBlockReason())
                    || item.getBlockingChecklistCount() > 0;
            if (blocked) {
                items.add(attention("Authority",
                        firstText(item.getPermitTypeName(), item.getCaseNumber()) + " is blocked",
                        "HIGH",
                        item.getAssignedToName(),
                        firstText(item.getBlockReason(), "Clear the blocker before submission")));
            } else if (status == ApprovalCaseStatus.EXPIRING_SOON
                    || (item.getDaysToExpiry() != null && item.getDaysToExpiry() >= 0 && item.getDaysToExpiry() <= 30)) {
                items.add(attention("Authority",
                        firstText(item.getPermitTypeName(), item.getCaseNumber()) + " is expiring",
                        "HIGH",
                        item.getAssignedToName(),
                        item.getExpiryDate() == null ? "Renew the permit" : "Expires " + item.getExpiryDate()));
            }
        }
        long overdueTasks = tasks.stream().filter(t -> isOverdue(t, today)).count();
        if (overdueTasks > 0) {
            items.add(attention("Tasks", overdueTasks + " task(s) are overdue", "HIGH", project.getAssignedManager(),
                    "Review overdue room tasks"));
        }
        long severeSnags = snags.stream()
                .filter(s -> s.getStatus() != SnagStatus.CLOSED)
                .filter(s -> s.getSeverity() == SnagSeverity.HIGH || s.getSeverity() == SnagSeverity.CRITICAL)
                .count();
        if (severeSnags > 0) {
            items.add(attention("Snags", severeSnags + " high-severity snag(s) are open", "HIGH",
                    project.getAssignedManager(), "Close or inspect the snags"));
        }
        long overdueInvoices = milestones.stream().filter(m -> {
            PaymentRequestResponse payment = m.getLatestPaymentRequest();
            BillingStatus status = payment != null && payment.getStatus() != null ? payment.getStatus() : m.getStatus();
            return m.getDueDate() != null && m.getDueDate().isBefore(today)
                    && status != BillingStatus.PAID
                    && status != null && INVOICED.contains(status);
        }).count();
        if (overdueInvoices == 0) {
            overdueInvoices = invoices.stream()
                    .filter(i -> i.getStatus() != BillingStatus.PAID)
                    .count() > 0 && milestones.isEmpty() ? 0 : overdueInvoices;
        }
        if (overdueInvoices > 0) {
            items.add(attention("Billing", overdueInvoices + " invoice(s) are past due", "HIGH",
                    project.getAssignedManager(), "Follow up collection"));
        }
        long pendingVariations = variations.stream()
                .filter(v -> v.getStatus() != null && PENDING_VARIATION.contains(v.getStatus()))
                .count();
        if (pendingVariations > 0) {
            items.add(attention("Variations", pendingVariations + " variation(s) are pending", "MEDIUM",
                    project.getAssignedManager(), "Complete review or client decision"));
        }
        return items.stream().limit(ATTENTION_LIMIT).toList();
    }

    private FinalProjectReportResponse.ClientBlock buildClient(Project project, Account account, String clientName) {
        if (project.getClientId() == null && !StringUtils.hasText(clientName)) {
            return FinalProjectReportResponse.ClientBlock.builder().assigned(false).build();
        }
        String contact = null;
        String accountLabel = null;
        if (account != null) {
            contact = firstText(account.getPhone(), account.getEmail());
            accountLabel = firstText(account.getEmail(), account.getCompanyName());
        }
        return FinalProjectReportResponse.ClientBlock.builder()
                .assigned(true)
                .name(clientName)
                .clientId(project.getClientId() == null ? null : String.valueOf(project.getClientId()))
                .account(accountLabel)
                .location(location(project))
                .contact(contact)
                .build();
    }

    private List<SnagResponse> loadSnags(Long projectId) {
        if (isPureClient()) {
            return safeList("snags", () -> snagService.listClientVisible(projectId));
        }
        List<SnagResponse> staff = safeList("snags", () -> snagService.list(projectId));
        if (!staff.isEmpty() || !isPureClient()) {
            return staff.stream().filter(s -> projectId.equals(s.getProjectId())).toList();
        }
        return List.of();
    }

    private List<ProjectDocumentResponse> loadDocuments(Long projectId, boolean internal) {
        if (internal) {
            return safeList("documents", () -> projectDocumentService.list(projectId)).stream()
                    .filter(d -> !d.isDeleted())
                    .filter(d -> projectId.equals(d.getProjectId()))
                    .toList();
        }
        return safeList("documents", () -> projectDocumentService.listPublished(projectId)).stream()
                .filter(d -> projectId.equals(d.getProjectId()))
                .toList();
    }

    private Account loadClient(Project project) {
        if (project.getClientId() == null) return null;
        return accountRepository.findById(project.getClientId())
                .filter(account -> account.getCompany() == null
                        || project.getCompanyId() == null
                        || project.getCompanyId().equals(account.getCompany().getUuid()))
                .orElse(null);
    }

    private String loadNature(Project project) {
        if (project.getApprovalProjectNatureId() == null) return null;
        return projectNatureRepository.findByIdAndDeletedFalse(project.getApprovalProjectNatureId())
                .filter(n -> project.getCompanyId() == null || project.getCompanyId().equals(n.getCompanyId()))
                .map(ApprovalProjectNature::getName)
                .orElse(null);
    }

    private BigDecimal contractValue(ProjectCommercialResponse commercial, Project project) {
        if (commercial != null && isPositive(commercial.getCurrentContractValue())) {
            return commercial.getCurrentContractValue();
        }
        if (isPositive(project.getBudget())) {
            return project.getBudget();
        }
        return null;
    }

    private BigDecimal overallProgress(List<ScheduleActivityResponse> publishedActivities, Project project) {
        if (publishedActivities != null && !publishedActivities.isEmpty()) {
            return weighted(publishedActivities);
        }
        if (project.getProgress() != null) {
            return BigDecimal.valueOf(project.getProgress());
        }
        return null;
    }

    private String progressSource(List<ScheduleActivityResponse> publishedActivities, Project project) {
        if (publishedActivities != null && !publishedActivities.isEmpty()) {
            return "Published programme";
        }
        if (project.getProgress() != null) {
            return "Stored project progress";
        }
        return null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BoqDocumentResponse pickApproved(List<BoqDocumentResponse> boqs) {
        return boqs.stream()
                .filter(d -> d.getStatus() == BoqDocumentStatus.APPROVED || d.getStatus() == BoqDocumentStatus.FINAL)
                .max(Comparator.comparing(this::boqSortDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private java.time.LocalDateTime boqSortDate(BoqDocumentResponse doc) {
        if (doc.getApprovedAt() != null) return doc.getApprovedAt();
        return doc.getCreatedAt();
    }

    private String boqDate(BoqDocumentResponse doc) {
        if (doc.getApprovedAt() != null) return doc.getApprovedAt().toLocalDate().toString();
        if (doc.getCreatedAt() != null) return doc.getCreatedAt().toLocalDate().toString();
        return null;
    }

    static BigDecimal weighted(List<ScheduleActivityResponse> activities) {
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (ScheduleActivityResponse activity : activities) {
            BigDecimal weight = activity.getWeight() != null && activity.getWeight().signum() > 0
                    ? activity.getWeight() : BigDecimal.ONE;
            weightSum = weightSum.add(weight);
            weighted = weighted.add(weight.multiply(BigDecimal.valueOf(activity.getPercentComplete())));
        }
        if (weightSum.signum() <= 0) return BigDecimal.ZERO;
        return weighted.divide(weightSum, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal packageProgress(SubcontractorPackageResponse pkg) {
        if (pkg.getBoqPlannedQty() == null || pkg.getBoqPlannedQty().signum() <= 0
                || pkg.getApprovedClaimedQty() == null) {
            return null;
        }
        BigDecimal pct = pkg.getApprovedClaimedQty()
                .multiply(BigDecimal.valueOf(100))
                .divide(pkg.getBoqPlannedQty(), 0, RoundingMode.HALF_UP);
        if (pct.compareTo(BigDecimal.valueOf(100)) > 0) return BigDecimal.valueOf(100);
        if (pct.signum() < 0) return BigDecimal.ZERO;
        return pct;
    }

    private boolean taskDone(RoomTaskResponse task) {
        return task.getStatus() == RoomTaskStatus.APPROVED || task.getStatus() == RoomTaskStatus.CLOSED;
    }

    private boolean isOverdue(RoomTaskResponse task, LocalDate today) {
        return !taskDone(task)
                && task.getClientDeadline() != null
                && task.getClientDeadline().toLocalDate().isBefore(today);
    }

    private int taskRank(RoomTaskResponse task, LocalDate today) {
        if (isOverdue(task, today)) return 0;
        if (task.getStatus() == RoomTaskStatus.CHANGES_REQUESTED || task.getStatus() == RoomTaskStatus.AWAITING_CLIENT) {
            return 1;
        }
        if (task.getStatus() == RoomTaskStatus.OPEN) return 2;
        return 3;
    }

    private int severityRank(SnagResponse snag) {
        if (snag.getSeverity() == SnagSeverity.CRITICAL) return 0;
        if (snag.getSeverity() == SnagSeverity.HIGH) return 1;
        if (snag.getSeverity() == SnagSeverity.MEDIUM) return 2;
        return 3;
    }

    private String documentBucket(String category, String sourceType) {
        String text = ((category == null ? "" : category) + " " + (sourceType == null ? "" : sourceType))
                .toUpperCase(Locale.ROOT);
        if (text.contains("AS-BUILT") || text.contains("ASBUILT") || text.contains("AS_BUILT")) return "As-Builts";
        if (text.contains("O&M") || text.contains("OPERATION") || text.contains("MAINTENANCE")) return "O&M";
        if (text.contains("SUBMIT")) return "Submittals";
        if (text.contains("VALID")) return "Validation";
        if (text.contains("COMPLET") || text.contains("CLOSEOUT") || text.contains("HANDOVER")) return "Completion";
        if (text.contains("DRAW") || "DRAWING".equalsIgnoreCase(sourceType)) return "Drawings";
        return "Other documents";
    }

    private String developer(Project project) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(project.getCommunityName())) parts.add(project.getCommunityName().trim());
        if (StringUtils.hasText(project.getBuildingName())) parts.add(project.getBuildingName().trim());
        if (StringUtils.hasText(project.getEmirate())) parts.add(project.getEmirate().trim());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private String location(Project project) {
        if (StringUtils.hasText(project.getLocation())) return project.getLocation().trim();
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(project.getPlotZone())) parts.add(project.getPlotZone().trim());
        if (StringUtils.hasText(project.getBuildingName())) parts.add(project.getBuildingName().trim());
        if (StringUtils.hasText(project.getCommunityName())) parts.add(project.getCommunityName().trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String roomLabel(String floor, String name) {
        if (StringUtils.hasText(floor) && StringUtils.hasText(name)) return floor.trim() + " · " + name.trim();
        if (StringUtils.hasText(name)) return name.trim();
        if (StringUtils.hasText(floor)) return floor.trim();
        return "General";
    }

    private TimelineEvent event(OffsetDateTime at, String name, String description) {
        return TimelineEvent.builder()
                .at(at == null ? null : at.toString())
                .event(name)
                .description(blankToNull(description))
                .build();
    }

    private AttentionItem attention(String area, String issue, String priority, String owner, String next) {
        return AttentionItem.builder()
                .area(area)
                .issue(issue)
                .priority(priority)
                .owner(blankToNull(owner))
                .nextAction(next)
                .build();
    }

    private CountRow count(String label, int count) {
        return CountRow.builder().label(label).count(count).build();
    }

    private ApprovalCaseStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        try {
            return ApprovalCaseStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean canSeeInternalCommercial() {
        Set<Role> roles = currentRoles();
        if (roles.isEmpty()) return false;
        return roles.stream().anyMatch(role -> role != Role.CLIENT && role != Role.SUBCONTRACTOR);
    }

    private boolean isPureClient() {
        Set<Role> roles = currentRoles();
        return !roles.isEmpty() && roles.stream().allMatch(role -> role == Role.CLIENT);
    }

    private Set<Role> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Set.of();
        }
        return principal.getRoles() == null ? Set.of() : principal.getRoles();
    }

    private <T> T safe(String section, Supplier<T> call) {
        try {
            return call.get();
        } catch (ForbiddenException | UnauthorizedException ex) {
            log.debug("Final report omitted {}: {}", section, ex.getMessage());
            return null;
        }
    }

    private <T> List<T> safeList(String section, Supplier<List<T>> call) {
        List<T> value = safe(section, call);
        return value == null ? List.of() : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().filter(StringUtils::hasText).reduce((a, b) -> a + "; " + b).orElse(null);
    }
}
