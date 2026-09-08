package com.fitouts.approval.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.approval.api.ApprovalCaseDetailResponse;
import com.fitouts.approval.api.ApprovalCaseResponse;
import com.fitouts.approval.api.ApprovalResolveRequest;
import com.fitouts.approval.api.ApprovalResolveResponse;
import com.fitouts.approval.api.CaseCommentRequest;
import com.fitouts.approval.api.CaseCommentResponse;
import com.fitouts.approval.api.CaseEventResponse;
import com.fitouts.approval.api.CaseFeeRequest;
import com.fitouts.approval.api.CaseFeeResponse;
import com.fitouts.approval.api.CaseChecklistItemResponse;
import com.fitouts.approval.api.CaseStatusPatchRequest;
import com.fitouts.approval.api.CaseSubmissionRequest;
import com.fitouts.approval.api.CaseSubmissionResponse;
import com.fitouts.approval.api.ChecklistAttachRequest;
import com.fitouts.approval.api.PackAssemblyResponse;
import com.fitouts.approval.api.ProjectScopeToggles;
import com.fitouts.approval.api.ResolvedCaseView;
import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.ApprovalCaseEvent;
import com.fitouts.approval.domain.ApprovalCaseEventRepository;
import com.fitouts.approval.domain.ApprovalCaseRepository;
import com.fitouts.approval.domain.ApprovalCaseStatus;
import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.CaseChecklistItem;
import com.fitouts.approval.domain.CaseChecklistItemRepository;
import com.fitouts.approval.domain.CaseComment;
import com.fitouts.approval.domain.CaseCommentRepository;
import com.fitouts.approval.domain.CaseFee;
import com.fitouts.approval.domain.CaseFeeRepository;
import com.fitouts.approval.domain.CaseSubmission;
import com.fitouts.approval.domain.CaseSubmissionRepository;
import com.fitouts.approval.domain.DocumentType;
import com.fitouts.approval.domain.PermitType;
import com.fitouts.approval.domain.PermitTypeRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.schedule.application.ApprovalScheduleSync;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The approval case workflow: resolve, generate, track, submit, approve, expire and refund.
 *
 * <p>The gates here are the point of the module. A case cannot reach READY_TO_SUBMIT with a
 * required document missing or expired unless a director waives it with a reason, and cannot
 * be submitted while a prerequisite case is unapproved. Those checks live in the service, not
 * in the UI, so they hold whoever calls the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalCaseService {

    /** Days before expiry at which alerts fire, per the spec. */
    private static final int[] EXPIRY_ALERT_THRESHOLDS = {30, 14, 7, 1};

    /** Below this many closed cases, planning keeps using the seeded SLA estimate. */
    private static final int ACTUAL_SLA_MIN_SAMPLE = 10;

    private final ApprovalCaseRepository caseRepository;
    private final CaseChecklistItemRepository checklistRepository;
    private final CaseSubmissionRepository submissionRepository;
    private final CaseCommentRepository commentRepository;
    private final CaseFeeRepository feeRepository;
    private final ApprovalCaseEventRepository eventRepository;
    private final PermitTypeRepository permitTypeRepository;
    private final ApprovalLibraryService libraryService;
    private final JurisdictionResolver resolver;
    private final CaseNumberGenerator caseNumberGenerator;
    private final SubmissionPackAssembler packAssembler;
    private final NotificationService notificationService;
    private final ApprovalScheduleSync approvalScheduleSync;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    // Resolve and generate

    /** Previews the case set without writing anything. */
    @Transactional
    public ApprovalResolveResponse resolve(Long projectId, ApprovalResolveRequest request) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        Location location = resolveLocation(project, request);
        if (Boolean.TRUE.equals(request != null ? request.getSaveToProject() : null)) {
            persistLocation(project, location);
        }

        JurisdictionResolver.Resolution resolution = resolver.resolve(
                companyId, location.emirate(), location.community(), location.building(), location.scope());

        Map<String, ApprovalCase> existing = new LinkedHashMap<>();
        for (ApprovalCase c : caseRepository.findByProjectIdAndCompanyIdOrderByCreatedAtAsc(projectId, companyId)) {
            existing.put(c.getPermitTypeCode(), c);
        }

        List<ResolvedCaseView> cases = proposeCases(companyId, resolution, location, existing.keySet());
        return ApprovalResolveResponse.builder()
                .projectId(projectId)
                .emirate(location.emirate())
                .communityName(location.community())
                .buildingName(location.building())
                .jurisdictionMatched(resolution.isJurisdictionMatched())
                .jurisdictionUnverified(resolution.isJurisdictionUnverified())
                .matchNote(resolution.getMatchNote())
                .authorities(resolution.getAuthorities())
                .cases(cases)
                .warnings(resolution.getWarnings())
                .build();
    }

    /** Creates the resolved cases. Existing cases for the same permit are left alone. */
    @Transactional
    public List<ApprovalCaseResponse> generate(Long projectId, ApprovalResolveRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        Location location = resolveLocation(project, request);
        persistLocation(project, location);

        JurisdictionResolver.Resolution resolution = resolver.resolve(
                companyId, location.emirate(), location.community(), location.building(), location.scope());

        Map<String, ApprovalCase> existing = new LinkedHashMap<>();
        for (ApprovalCase c : caseRepository.findByProjectIdAndCompanyIdOrderByCreatedAtAsc(projectId, companyId)) {
            existing.put(c.getPermitTypeCode(), c);
        }

        Map<String, DocumentType> documentTypes = libraryService.documentTypeIndex(companyId);
        for (ResolvedCaseView proposal : proposeCases(companyId, resolution, location, existing.keySet())) {
            if (proposal.isAlreadyExists()) continue;
            ApprovalCase created = createCase(project, companyId, proposal, principal, documentTypes);
            existing.put(created.getPermitTypeCode(), created);
        }

        return listForProject(projectId);
    }

    private ApprovalCase createCase(Project project, UUID companyId, ResolvedCaseView proposal,
                                    AuthPrincipal principal, Map<String, DocumentType> documentTypes) {
        ApprovalCase approvalCase = new ApprovalCase();
        approvalCase.setProjectId(project.getId());
        approvalCase.setCompanyId(companyId);
        approvalCase.setPermitTypeCode(proposal.getPermitTypeCode());
        approvalCase.setPermitTypeName(proposal.getPermitTypeName());
        approvalCase.setAuthorityCode(proposal.getAuthorityCode());
        approvalCase.setAuthorityName(proposal.getAuthorityName());
        approvalCase.setCaseNumber(caseNumberGenerator.next(companyId, proposal.getPermitTypeCode()));
        approvalCase.setStatus(ApprovalCaseStatus.NOT_STARTED);
        approvalCase.setSlaDays(proposal.getSlaDays());
        approvalCase.setPrerequisitePermitCodes(
                SeedValueParser.joinList(proposal.getPrerequisitePermitCodes()));
        approvalCase.setBlocksActivityCodes(proposal.getBlocksActivitiesRaw());
        approvalCase.setCreatedBy(principal.getAccountId());
        if (proposal.isHasDeposit() || proposal.isDepositConditional()) {
            approvalCase.setDepositStatus("NOT_PAID");
        }
        caseRepository.save(approvalCase);

        seedChecklist(approvalCase, proposal.getRequiredDocumentCodes(), documentTypes);
        recordEvent(approvalCase, null, ApprovalCaseStatus.NOT_STARTED, "GENERATED",
                proposal.getTriggerReason(), principal.getAccountId());
        return approvalCase;
    }

    /**
     * Creates checklist rows for a case. The seed rarely lists required documents per permit,
     * so where it does not, a sensible baseline set is used and flagged in the item source.
     */
    private void seedChecklist(ApprovalCase approvalCase, List<String> requiredCodes,
                               Map<String, DocumentType> documentTypes) {
        List<String> codes = requiredCodes != null && !requiredCodes.isEmpty()
                ? requiredCodes
                : defaultChecklistFor(approvalCase.getPermitTypeCode());

        int order = 0;
        for (String code : codes) {
            CaseChecklistItem item = new CaseChecklistItem();
            item.setCaseUuid(approvalCase.getUuid());
            item.setDocumentTypeCode(code);
            DocumentType type = documentTypes.get(code);
            item.setDocumentTypeName(type != null ? type.getName() : code);
            item.setRequired(true);
            item.setStatus("MISSING");
            item.setSortOrder(order++);
            checklistRepository.save(item);
        }
    }

    /**
     * Baseline checklists used when the seed's permit rows carry no document codes.
     * Every UAE community and regulator submission needs the licence, insurances and a
     * method statement; the rest is permit specific.
     */
    private List<String> defaultChecklistFor(String permitCode) {
        List<String> base = new ArrayList<>(List.of("D01", "D04", "D05", "D06"));
        switch (permitCode) {
            case "P-COMM-REG" -> base.addAll(List.of("D02", "D03", "D07", "D26"));
            case "P-COMM-NOC", "P-BLDG-NOC" -> base.addAll(List.of("D08", "D14", "D20", "D21", "D22", "D27", "D28", "D29"));
            case "P-ACCESS" -> base.addAll(List.of("D02", "D23", "D24"));
            case "P-DEMO" -> base.addAll(List.of("D13", "D17", "D20", "D22"));
            case "P-MOD" -> base.addAll(List.of("D07", "D12", "D13", "D16", "D17", "D18"));
            case "P-FITOUT" -> base.addAll(List.of("D15", "D16", "D18", "D20", "D27"));
            case "P-DCD-NOC" -> base.addAll(List.of("D09", "D19"));
            case "P-DCD-FINAL" -> base.addAll(List.of("D09", "D19", "D33"));
            case "P-DEWA-TEMP", "P-RECONNECT", "P-LOAD" -> base.addAll(List.of("D11", "D18"));
            case "P-DISCONNECT" -> base.addAll(List.of("D11", "D14"));
            case "P-SIRA" -> base.addAll(List.of("D10", "D18"));
            case "P-RTA" -> base.addAll(List.of("D13", "D20"));
            case "P-WASTE" -> base.addAll(List.of("D22"));
            case "P-SIGN" -> base.addAll(List.of("D15", "D16"));
            case "P-KITCHEN" -> base.addAll(List.of("D16", "D18"));
            case "P-COMPLETE" -> base.addAll(List.of("D32", "D33"));
            case "P-DEPOSIT" -> {
                base.clear();
                base.addAll(List.of("D34", "D35", "D29"));
            }
            default -> { }
        }
        return base.stream().distinct().toList();
    }

    /**
     * Walks the permit catalogue and keeps the permits whose issuing body is in scope and
     * whose trigger the project's scope satisfies.
     */
    private List<ResolvedCaseView> proposeCases(UUID companyId,
                                                JurisdictionResolver.Resolution resolution,
                                                Location location,
                                                java.util.Set<String> existingCodes) {
        Map<String, Authority> authorities = libraryService.authorityIndex(companyId);
        PermitTriggerRules.Context context = new PermitTriggerRules.Context(
                location.scope(),
                StringUtils.hasText(location.building()),
                location.newBuild());

        List<ResolvedCaseView> out = new ArrayList<>();
        for (PermitType permit : permitTypeRepository.findVisible(companyId)) {
            if (!PermitTriggerRules.applies(permit.getCode(), context)) continue;

            Authority issuing = bindAuthority(permit, resolution, authorities);
            if (issuing == null) continue;

            Integer sla = permit.planningSlaDays();
            boolean usingActual = permit.getActualMedianSlaDays() != null
                    && permit.getCompletedCaseCount() >= ACTUAL_SLA_MIN_SAMPLE;

            out.add(ResolvedCaseView.builder()
                    .permitTypeCode(permit.getCode())
                    .permitTypeName(permit.getName())
                    .authorityCode(issuing.getCode())
                    .authorityName(issuing.getName())
                    .authorityType(issuing.getType().name())
                    .slaDays(sla)
                    .slaSource(usingActual
                            ? "Median of " + permit.getCompletedCaseCount() + " completed cases"
                            : "Seeded estimate " + permit.getIndicativeSlaRaw())
                    .validityDays(permit.getValidityDaysMax())
                    .hasDeposit(permit.isHasDeposit())
                    .depositConditional(permit.isDepositConditional())
                    .depositAmountIndicative(permit.getDepositAmountIndicative())
                    .feeIndicative(permit.getFeeIndicative())
                    .prerequisitePermitCodes(SeedValueParser.splitList(permit.getPrerequisitePermitCodes()))
                    .requiredDocumentCodes(SeedValueParser.splitList(permit.getRequiredDocumentCodes()))
                    .blocksActivitiesRaw(permit.getBlocksActivitiesRaw())
                    .triggerReason(PermitTriggerRules.reason(permit.getCode(), permit.getTypicalTrigger()))
                    .alreadyExists(existingCodes.contains(permit.getCode()))
                    .build());
        }

        out.sort(Comparator.comparing(ResolvedCaseView::getPermitTypeCode));
        return out;
    }

    /**
     * Picks the authority that will actually issue this permit.
     *
     * <p>Some catalogue rows name a specific body ("Dubai Civil Defence"); others name a
     * layer ("Master Developer"), because which developer applies depends on the community.
     * A permit whose layer has no authority in scope is dropped rather than guessed at.
     */
    private Authority bindAuthority(PermitType permit,
                                    JurisdictionResolver.Resolution resolution,
                                    Map<String, Authority> authorities) {
        if (permit.getAuthorityCode() != null) {
            return resolution.hasAuthority(permit.getAuthorityCode())
                    ? authorities.get(permit.getAuthorityCode())
                    : null;
        }
        if (permit.getAuthorityType() == null) return null;

        AuthorityType layer = AuthorityType.fromLabel(permit.getAuthorityType());
        return resolution.byType(layer)
                .map(view -> authorities.get(view.getCode()))
                .orElse(null);
    }

    // Reading

    @Transactional(readOnly = true)
    public List<ApprovalCaseResponse> listForProject(Long projectId) {
        requireStaff();
        requireProject(projectId);
        UUID companyId = CompanyContext.get();
        List<ApprovalCase> cases = caseRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtAsc(projectId, companyId);
        return toResponses(cases, companyId);
    }

    /** Every live case across every project. The PRO's daily screen. */
    @Transactional(readOnly = true)
    public List<ApprovalCaseResponse> dashboard(String status, String authorityCode, Long ownerAccountId) {
        requireStaff();
        UUID companyId = requireCompany();
        List<ApprovalCase> cases = caseRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(c.getStatus().name()))
                .filter(c -> authorityCode == null || authorityCode.isBlank()
                        || authorityCode.equalsIgnoreCase(c.getAuthorityCode()))
                .filter(c -> ownerAccountId == null || ownerAccountId.equals(c.getAssignedToAccountId()))
                .toList();
        return toResponses(cases, companyId);
    }

    @Transactional(readOnly = true)
    public ApprovalCaseDetailResponse get(UUID caseUuid) {
        requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        List<CaseChecklistItem> checklist = checklistRepository
                .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(caseUuid);
        Map<String, DocumentType> documentTypes = libraryService.documentTypeIndex(companyId);

        return ApprovalCaseDetailResponse.builder()
                .header(toResponse(approvalCase, checklist, companyId, projectNames(companyId), accountNames()))
                .checklist(checklist.stream().map(i -> toChecklistItem(i, documentTypes)).toList())
                .submissions(submissionRepository.findByCaseUuidOrderByVersionAsc(caseUuid)
                        .stream().map(this::toSubmission).toList())
                .comments(commentRepository.findByCaseUuidOrderByRaisedDateAsc(caseUuid)
                        .stream().map(this::toComment).toList())
                .fees(feeRepository.findByCaseUuidOrderByCreatedAtAsc(caseUuid)
                        .stream().map(f -> toFee(f, approvalCase, null)).toList())
                .events(eventRepository.findByCaseUuidOrderByCreatedAtDesc(caseUuid)
                        .stream().map(this::toEvent).toList())
                .allowedTransitions(approvalCase.getStatus().allowedNext().stream()
                        .map(Enum::name).sorted().toList())
                .build();
    }

    // Status transitions

    @Transactional
    public ApprovalCaseDetailResponse patch(UUID caseUuid, CaseStatusPatchRequest request) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        if (request == null) {
            throw new BadRequestException("Nothing to update");
        }

        if (request.getAssignedToAccountId() != null) {
            approvalCase.setAssignedToAccountId(request.getAssignedToAccountId());
        }
        if (request.getTargetSubmissionDate() != null) {
            approvalCase.setTargetSubmissionDate(request.getTargetSubmissionDate());
        }
        if (request.getAuthorityReference() != null) {
            approvalCase.setAuthorityReference(SeedValueParser.trimToNull(request.getAuthorityReference()));
        }
        if (request.getNotes() != null) {
            approvalCase.setNotes(request.getNotes());
        }
        if (request.getLinkedActivityUuids() != null) {
            approvalCase.setLinkedActivityUuids(SeedValueParser.joinList(request.getLinkedActivityUuids()));
        }

        if (StringUtils.hasText(request.getStatus())) {
            applyTransition(approvalCase, request, principal, companyId);
            // A permit clearing or lapsing changes what the site can start, so the programme
            // is re-solved here rather than waiting for someone to open the schedule tab.
            syncSchedule(approvalCase, companyId);
        } else {
            caseRepository.save(approvalCase);
        }
        return get(caseUuid);
    }

    /**
     * Pushes this case's new position onto the Gantt. Failures are logged and swallowed: a
     * project without a programme yet is normal, and a scheduling problem must not roll back
     * the PRO's record of what the authority actually said.
     */
    private void syncSchedule(ApprovalCase approvalCase, UUID companyId) {
        if (approvalCase.getBlocksActivityCodes() == null
                || approvalCase.getBlocksActivityCodes().isBlank()) {
            return;
        }
        try {
            List<String> notes = approvalScheduleSync.syncProject(approvalCase.getProjectId(), companyId);
            for (String note : notes) {
                notificationService.raise(new NotificationService.Alert(
                        companyId,
                        approvalCase.getAssignedToAccountId(),
                        "SCHEDULE_CONSTRAINT",
                        "WARNING",
                        "Programme held by " + approvalCase.getPermitTypeName(),
                        note,
                        "/admin/projects/" + approvalCase.getProjectId() + "/schedule",
                        "APPROVAL_CASE",
                        approvalCase.getUuid(),
                        "constraint:" + approvalCase.getUuid() + ":" + note.hashCode(),
                        false));
            }
        } catch (Exception e) {
            log.warn("Could not resync the programme for project {} after case {} changed: {}",
                    approvalCase.getProjectId(), approvalCase.getCaseNumber(), e.getMessage());
        }
    }

    private void applyTransition(ApprovalCase approvalCase, CaseStatusPatchRequest request,
                                 AuthPrincipal principal, UUID companyId) {
        ApprovalCaseStatus from = approvalCase.getStatus();
        ApprovalCaseStatus to;
        try {
            to = ApprovalCaseStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status " + request.getStatus());
        }
        if (from == to) {
            caseRepository.save(approvalCase);
            return;
        }
        if (!from.canMoveTo(to)) {
            throw new BadRequestException("A case in " + from + " cannot move to " + to
                    + ". Allowed: " + approvalCase.getStatus().allowedNext());
        }

        switch (to) {
            case READY_TO_SUBMIT -> requireChecklistComplete(approvalCase);
            case SUBMITTED, RESUBMITTED -> {
                requireChecklistComplete(approvalCase);
                requirePrerequisitesApproved(approvalCase, companyId);
                startSlaClock(approvalCase, LocalDate.now());
            }
            case APPROVED -> {
                approvalCase.setApprovedDate(request.getApprovedDate() != null
                        ? request.getApprovedDate() : LocalDate.now());
                closeOutSubmission(approvalCase, "APPROVED", approvalCase.getApprovedDate());
                recordActualSla(approvalCase, companyId);
            }
            case ISSUED -> {
                if (!StringUtils.hasText(request.getPermitNumber()) && !StringUtils.hasText(approvalCase.getPermitNumber())) {
                    throw new BadRequestException("A permit number is required to mark a case as issued");
                }
                approvalCase.setPermitNumber(firstNonBlank(request.getPermitNumber(), approvalCase.getPermitNumber()));
                approvalCase.setPermitFilePath(firstNonBlank(request.getPermitFilePath(), approvalCase.getPermitFilePath()));
                approvalCase.setIssueDate(request.getIssueDate() != null
                        ? request.getIssueDate() : LocalDate.now());
                approvalCase.setExpiryDate(resolveExpiry(approvalCase, request, companyId));
                spawnDepositRefundCaseIfNeeded(approvalCase, principal, companyId);
            }
            case REJECTED, WITHDRAWN, CLOSED -> {
                if (!StringUtils.hasText(request.getReason())) {
                    throw new BadRequestException("A reason is required to " + to.name().toLowerCase(Locale.ROOT) + " a case");
                }
                approvalCase.setClosedReason(request.getReason().trim());
                closeOutSubmission(approvalCase, to == ApprovalCaseStatus.REJECTED ? "REJECTED" : "PENDING", LocalDate.now());
            }
            case COMMENTS_RECEIVED -> closeOutSubmission(approvalCase, "COMMENTS", LocalDate.now());
            default -> { }
        }

        approvalCase.setStatus(to);
        caseRepository.save(approvalCase);
        recordEvent(approvalCase, from, to, "STATUS_CHANGE", request.getReason(), principal.getAccountId());
    }

    /** Blocks the move when a required document is missing or expired, and says which. */
    private void requireChecklistComplete(ApprovalCase approvalCase) {
        List<CaseChecklistItem> items = checklistRepository
                .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(approvalCase.getUuid());
        List<String> blocking = new ArrayList<>();
        for (CaseChecklistItem item : items) {
            item.refreshStatus();
            if (item.isBlocking()) {
                blocking.add(item.getDocumentTypeCode() + " " + item.getDocumentTypeName()
                        + " (" + item.getStatus().toLowerCase(Locale.ROOT) + ")");
            }
        }
        checklistRepository.saveAll(items);
        if (!blocking.isEmpty()) {
            throw new BadRequestException("The pack is incomplete: " + String.join("; ", blocking)
                    + ". Attach the documents, or have a director waive them with a reason.");
        }
    }

    /** Blocks submission while a prerequisite case on the same project is unapproved. */
    private void requirePrerequisitesApproved(ApprovalCase approvalCase, UUID companyId) {
        List<String> unmet = unmetPrerequisites(approvalCase, companyId);
        if (!unmet.isEmpty()) {
            throw new BadRequestException("These prerequisite approvals are not granted yet: "
                    + String.join(", ", unmet));
        }
    }

    private List<String> unmetPrerequisites(ApprovalCase approvalCase, UUID companyId) {
        List<String> codes = SeedValueParser.splitList(approvalCase.getPrerequisitePermitCodes());
        if (codes.isEmpty()) return List.of();

        Map<String, ApprovalCase> byCode = new LinkedHashMap<>();
        for (ApprovalCase c : caseRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtAsc(approvalCase.getProjectId(), companyId)) {
            byCode.put(c.getPermitTypeCode(), c);
        }

        List<String> unmet = new ArrayList<>();
        for (String code : codes) {
            ApprovalCase prerequisite = byCode.get(code);
            if (prerequisite == null || !prerequisite.getStatus().isApprovedOrLater()) {
                unmet.add(code);
            }
        }
        return unmet;
    }

    private void startSlaClock(ApprovalCase approvalCase, LocalDate submittedOn) {
        approvalCase.setSubmittedDate(submittedOn);
        Integer slaDays = approvalCase.getSlaDays();
        if (slaDays != null && slaDays > 0) {
            approvalCase.setSlaDueDate(addWorkingDays(submittedOn, slaDays));
        }
    }

    /** Fills in the expiry from the permit's validity when the PRO did not type one. */
    private LocalDate resolveExpiry(ApprovalCase approvalCase, CaseStatusPatchRequest request, UUID companyId) {
        if (request.getExpiryDate() != null) return request.getExpiryDate();
        if (approvalCase.getExpiryDate() != null) return approvalCase.getExpiryDate();

        PermitType permit = libraryService.permitTypeIndex(companyId).get(approvalCase.getPermitTypeCode());
        if (permit == null || permit.getValidityDaysMin() == null) return null;
        LocalDate from = approvalCase.getIssueDate() != null ? approvalCase.getIssueDate() : LocalDate.now();
        // The conservative bound: a 30-90 day validity is treated as 30 for alerting.
        return from.plusDays(permit.getValidityDaysMin());
    }

    /**
     * Opens the refund case as soon as a deposit-bearing permit is issued, so the money is
     * tracked from day one instead of being remembered at the end of the project.
     */
    private void spawnDepositRefundCaseIfNeeded(ApprovalCase approvalCase, AuthPrincipal principal, UUID companyId) {
        if (approvalCase.getDepositRefundCaseUuid() != null) return;
        PermitType permit = libraryService.permitTypeIndex(companyId).get(approvalCase.getPermitTypeCode());
        if (permit == null || !permit.isHasDeposit()) return;

        Optional<ApprovalCase> alreadyOpen = caseRepository
                .findByProjectIdAndCompanyIdAndPermitTypeCode(approvalCase.getProjectId(), companyId, "P-DEPOSIT");
        if (alreadyOpen.isPresent()) {
            approvalCase.setDepositRefundCaseUuid(alreadyOpen.get().getUuid());
            return;
        }

        PermitType depositPermit = libraryService.permitTypeIndex(companyId).get("P-DEPOSIT");
        ApprovalCase refund = new ApprovalCase();
        refund.setProjectId(approvalCase.getProjectId());
        refund.setCompanyId(companyId);
        refund.setPermitTypeCode("P-DEPOSIT");
        refund.setPermitTypeName(depositPermit != null ? depositPermit.getName() : "Security deposit refund claim");
        refund.setAuthorityCode(approvalCase.getAuthorityCode());
        refund.setAuthorityName(approvalCase.getAuthorityName());
        refund.setCaseNumber(caseNumberGenerator.next(companyId, "P-DEPOSIT"));
        refund.setStatus(ApprovalCaseStatus.NOT_STARTED);
        refund.setSlaDays(depositPermit != null ? depositPermit.planningSlaDays() : 30);
        refund.setNotes("Opened automatically when " + approvalCase.getCaseNumber() + " was issued.");
        refund.setCreatedBy(principal != null ? principal.getAccountId() : null);
        caseRepository.save(refund);

        seedChecklist(refund, List.of(), libraryService.documentTypeIndex(companyId));
        recordEvent(refund, null, ApprovalCaseStatus.NOT_STARTED, "DEPOSIT_REFUND_OPENED",
                "Deposit-bearing permit " + approvalCase.getCaseNumber() + " was issued",
                principal != null ? principal.getAccountId() : null);

        approvalCase.setDepositRefundCaseUuid(refund.getUuid());
    }

    /**
     * Records the observed turnaround against this permit type. Planning only switches from
     * the seeded estimate to the actual median once enough cases have closed.
     */
    private void recordActualSla(ApprovalCase approvalCase, UUID companyId) {
        if (approvalCase.getSubmittedDate() == null || approvalCase.getApprovedDate() == null) return;

        List<PermitType> matches = permitTypeRepository
                .findByCodeVisible(approvalCase.getPermitTypeCode(), companyId);
        if (matches.isEmpty()) return;
        PermitType permit = matches.get(0);

        List<Long> turnarounds = new ArrayList<>();
        for (ApprovalCase closed : caseRepository
                .findApprovedByPermitType(companyId, approvalCase.getPermitTypeCode())) {
            if (closed.getSubmittedDate() == null || closed.getApprovedDate() == null) continue;
            turnarounds.add(workingDaysBetween(closed.getSubmittedDate(), closed.getApprovedDate()));
        }
        if (turnarounds.isEmpty()) return;

        turnarounds.sort(Long::compareTo);
        long median = turnarounds.get(turnarounds.size() / 2);
        permit.setActualMedianSlaDays((int) median);
        permit.setCompletedCaseCount(turnarounds.size());
        permitTypeRepository.save(permit);
    }

    private void closeOutSubmission(ApprovalCase approvalCase, String outcome, LocalDate outcomeDate) {
        submissionRepository.findFirstByCaseUuidOrderByVersionDesc(approvalCase.getUuid())
                .filter(s -> "PENDING".equals(s.getOutcome()))
                .ifPresent(s -> {
                    s.setOutcome(outcome);
                    s.setOutcomeDate(outcomeDate);
                    s.setTurnaroundDays((int) workingDaysBetween(s.getSubmittedDate(), outcomeDate));
                    submissionRepository.save(s);
                });
    }

    // Checklist

    @Transactional
    public ApprovalCaseDetailResponse attachChecklistItem(UUID caseUuid, UUID itemUuid, ChecklistAttachRequest request) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        CaseChecklistItem item = checklistRepository.findByUuidAndCaseUuid(itemUuid, caseUuid)
                .orElseThrow(() -> new NotFoundException("Checklist item not found"));

        if (request != null) {
            if (request.getFilePath() != null) {
                item.setFilePath(SeedValueParser.trimToNull(request.getFilePath()));
                item.setSource("UPLOADED");
            }
            if (request.getExpiryDate() != null) item.setExpiryDate(request.getExpiryDate());
            if ("NOT_APPLICABLE".equalsIgnoreCase(request.getStatus())) {
                item.setStatus("NOT_APPLICABLE");
            } else {
                if ("NOT_APPLICABLE".equals(item.getStatus())) item.setStatus("MISSING");
                item.refreshStatus();
            }
        }
        checklistRepository.save(item);
        syncPreparationStatus(approvalCase, principal);
        return get(caseUuid);
    }

    /**
     * Director override to proceed with a missing or expired document.
     * Restricted deliberately: this is the one hole in the compliance gate.
     */
    @Transactional
    public ApprovalCaseDetailResponse waiveChecklistItem(UUID caseUuid, UUID itemUuid, ChecklistAttachRequest request) {
        AuthPrincipal principal = requireDirector();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        if (request == null || !StringUtils.hasText(request.getWaiverReason())) {
            throw new BadRequestException("A waiver reason is required");
        }
        CaseChecklistItem item = checklistRepository.findByUuidAndCaseUuid(itemUuid, caseUuid)
                .orElseThrow(() -> new NotFoundException("Checklist item not found"));

        item.setStatus("WAIVED");
        item.setWaivedBy(principal.getAccountId());
        item.setWaiverReason(request.getWaiverReason().trim());
        checklistRepository.save(item);

        recordEvent(approvalCase, approvalCase.getStatus(), approvalCase.getStatus(), "CHECKLIST_WAIVED",
                item.getDocumentTypeCode() + ": " + request.getWaiverReason().trim(), principal.getAccountId());
        syncPreparationStatus(approvalCase, principal);
        return get(caseUuid);
    }

    /** Moves a case between NOT_STARTED, PACK_IN_PREPARATION and READY_TO_SUBMIT as documents land. */
    private void syncPreparationStatus(ApprovalCase approvalCase, AuthPrincipal principal) {
        ApprovalCaseStatus current = approvalCase.getStatus();
        boolean prePreparation = current == ApprovalCaseStatus.NOT_STARTED
                || current == ApprovalCaseStatus.PACK_IN_PREPARATION
                || current == ApprovalCaseStatus.READY_TO_SUBMIT;
        if (!prePreparation) return;

        List<CaseChecklistItem> items = checklistRepository
                .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(approvalCase.getUuid());
        items.forEach(CaseChecklistItem::refreshStatus);
        checklistRepository.saveAll(items);

        boolean complete = items.stream().noneMatch(CaseChecklistItem::isBlocking);
        ApprovalCaseStatus next = complete ? ApprovalCaseStatus.READY_TO_SUBMIT : ApprovalCaseStatus.PACK_IN_PREPARATION;
        if (next != current) {
            approvalCase.setStatus(next);
            caseRepository.save(approvalCase);
            recordEvent(approvalCase, current, next, "PACK_STATUS", null,
                    principal != null ? principal.getAccountId() : null);
        }
    }

    // Pack assembly

    @Transactional
    public PackAssemblyResponse assemblePack(UUID caseUuid) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        List<CaseChecklistItem> items = checklistRepository
                .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(caseUuid);

        SubmissionPackAssembler.Collection collection = packAssembler.collect(
                approvalCase, items, libraryService.documentTypeIndex(companyId));
        checklistRepository.saveAll(items);

        String packPath = packAssembler.buildPack(approvalCase, items, collection, null);
        int collected = (int) items.stream()
                .filter(i -> i.getFilePath() != null && !i.getFilePath().isBlank())
                .count();

        recordEvent(approvalCase, approvalCase.getStatus(), approvalCase.getStatus(), "PACK_ASSEMBLED",
                collected + " documents, " + collection.missing().size() + " missing",
                principal.getAccountId());
        syncPreparationStatus(approvalCase, principal);

        boolean ready = collection.missing().isEmpty() && collection.expired().isEmpty();
        return PackAssemblyResponse.builder()
                .packFilePath(packPath)
                .packFileName(approvalCase.getCaseNumber() + "_pack.zip")
                .documentsCollected(collected)
                .autoCollected(collection.autoCollected())
                .missing(collection.missing())
                .expired(collection.expired())
                .waived(collection.waived())
                .readyToSubmit(ready)
                .note(ready
                        ? "Pack is complete and ready to submit."
                        : "The pack cannot be submitted until the missing and expired documents are resolved.")
                .build();
    }

    // Submissions, comments, fees

    @Transactional
    public ApprovalCaseDetailResponse addSubmission(UUID caseUuid, CaseSubmissionRequest request) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);

        requireChecklistComplete(approvalCase);
        requirePrerequisitesApproved(approvalCase, companyId);

        LocalDate submittedOn = request != null && request.getSubmittedDate() != null
                ? request.getSubmittedDate() : LocalDate.now();

        int version = approvalCase.getCurrentVersion() + 1;
        CaseSubmission submission = new CaseSubmission();
        submission.setCaseUuid(caseUuid);
        submission.setVersion(version);
        submission.setSubmittedBy(principal.getAccountId());
        submission.setSubmittedDate(submittedOn);
        if (request != null) {
            submission.setChannel(SeedValueParser.trimToNull(request.getChannel()));
            submission.setReceiptFilePath(SeedValueParser.trimToNull(request.getReceiptFilePath()));
            submission.setFeeAmount(request.getFeeAmount());
            submission.setNotes(request.getNotes());
            if (StringUtils.hasText(request.getOutcome())) {
                submission.setOutcome(request.getOutcome().trim().toUpperCase(Locale.ROOT));
                submission.setOutcomeDate(request.getOutcomeDate() != null
                        ? request.getOutcomeDate() : LocalDate.now());
                submission.setTurnaroundDays((int) workingDaysBetween(submittedOn, submission.getOutcomeDate()));
            }
        }
        submissionRepository.save(submission);

        ApprovalCaseStatus from = approvalCase.getStatus();
        approvalCase.setCurrentVersion(version);
        approvalCase.setStatus(version == 1 ? ApprovalCaseStatus.SUBMITTED : ApprovalCaseStatus.RESUBMITTED);
        if (request != null && StringUtils.hasText(request.getAuthorityReference())) {
            approvalCase.setAuthorityReference(request.getAuthorityReference().trim());
        }
        startSlaClock(approvalCase, submittedOn);
        if (request != null && request.getFeeAmount() != null) {
            approvalCase.setFeePaid(approvalCase.getFeePaid().add(request.getFeeAmount()));
        }
        caseRepository.save(approvalCase);

        recordEvent(approvalCase, from, approvalCase.getStatus(), "SUBMITTED",
                "Version " + version + (submission.getChannel() != null ? " via " + submission.getChannel() : ""),
                principal.getAccountId());
        return get(caseUuid);
    }

    @Transactional
    public ApprovalCaseDetailResponse addComment(UUID caseUuid, CaseCommentRequest request) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        if (request == null || !StringUtils.hasText(request.getCommentText())) {
            throw new BadRequestException("commentText is required");
        }

        CaseComment comment = new CaseComment();
        comment.setCaseUuid(caseUuid);
        comment.setSubmissionUuid(request.getSubmissionUuid() != null
                ? request.getSubmissionUuid()
                : submissionRepository.findFirstByCaseUuidOrderByVersionDesc(caseUuid)
                        .map(CaseSubmission::getUuid).orElse(null));
        comment.setCommentText(request.getCommentText().trim());
        comment.setReasonCode(SeedValueParser.trimToNull(request.getReasonCode()));
        comment.setRaisedDate(request.getRaisedDate() != null ? request.getRaisedDate() : LocalDate.now());
        comment.setResponseText(request.getResponseText());
        comment.setRespondedDate(request.getRespondedDate());
        comment.setResponsibleParty(SeedValueParser.trimToNull(request.getResponsibleParty()));
        comment.setCreatedBy(principal.getAccountId());
        commentRepository.save(comment);

        if (approvalCase.getStatus().canMoveTo(ApprovalCaseStatus.COMMENTS_RECEIVED)) {
            ApprovalCaseStatus from = approvalCase.getStatus();
            approvalCase.setStatus(ApprovalCaseStatus.COMMENTS_RECEIVED);
            closeOutSubmission(approvalCase, "COMMENTS", comment.getRaisedDate());
            caseRepository.save(approvalCase);
            recordEvent(approvalCase, from, ApprovalCaseStatus.COMMENTS_RECEIVED, "COMMENT_RECEIVED",
                    comment.getReasonCode(), principal.getAccountId());
        }
        return get(caseUuid);
    }

    @Transactional
    public ApprovalCaseDetailResponse saveFee(UUID caseUuid, CaseFeeRequest request) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase approvalCase = requireCase(caseUuid, companyId);
        if (request == null || request.getAmount() == null) {
            throw new BadRequestException("amount is required");
        }

        CaseFee fee = request.getUuid() != null
                ? feeRepository.findById(request.getUuid())
                        .orElseThrow(() -> new NotFoundException("Fee not found"))
                : new CaseFee();
        if (fee.getCaseUuid() != null && !fee.getCaseUuid().equals(caseUuid)) {
            throw new ForbiddenException("Fee belongs to another case");
        }

        fee.setCaseUuid(caseUuid);
        fee.setProjectId(approvalCase.getProjectId());
        fee.setCompanyId(companyId);
        String type = StringUtils.hasText(request.getType())
                ? request.getType().trim().toUpperCase(Locale.ROOT) : "FEE";
        fee.setType(type);
        fee.setAmount(request.getAmount());
        if (StringUtils.hasText(request.getCurrency())) fee.setCurrency(request.getCurrency().trim());
        fee.setPaidDate(request.getPaidDate());
        fee.setPaymentRef(SeedValueParser.trimToNull(request.getPaymentRef()));
        fee.setReceiptFilePath(SeedValueParser.trimToNull(request.getReceiptFilePath()));
        fee.setCostCode(SeedValueParser.trimToNull(request.getCostCode()));
        // A deposit is refundable unless told otherwise; that is the whole reason to track it.
        fee.setRefundable(request.getRefundable() != null ? request.getRefundable() : "DEPOSIT".equals(type));
        fee.setRefundClaimedDate(request.getRefundClaimedDate());
        fee.setRefundReceivedDate(request.getRefundReceivedDate());
        fee.setRefundAmount(request.getRefundAmount());
        fee.setNotes(request.getNotes());
        if (fee.getCreatedBy() == null) fee.setCreatedBy(principal.getAccountId());
        feeRepository.save(fee);

        recomputeCaseTotals(approvalCase);
        recordEvent(approvalCase, approvalCase.getStatus(), approvalCase.getStatus(), "FEE_RECORDED",
                type + " " + fee.getCurrency() + " " + fee.getAmount(), principal.getAccountId());
        return get(caseUuid);
    }

    private void recomputeCaseTotals(ApprovalCase approvalCase) {
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal deposits = BigDecimal.ZERO;
        boolean anyOutstanding = false;
        boolean anyDeposit = false;

        for (CaseFee fee : feeRepository.findByCaseUuidOrderByCreatedAtAsc(approvalCase.getUuid())) {
            if ("DEPOSIT".equals(fee.getType())) {
                anyDeposit = true;
                if (fee.getPaidDate() != null) deposits = deposits.add(fee.getAmount());
                if (fee.isOutstandingDeposit()) anyOutstanding = true;
            } else if (fee.getPaidDate() != null) {
                fees = fees.add(fee.getAmount());
            }
        }

        approvalCase.setFeePaid(fees);
        approvalCase.setDepositPaid(deposits);
        if (anyDeposit) {
            approvalCase.setDepositStatus(anyOutstanding ? "OUTSTANDING"
                    : deposits.signum() > 0 ? "REFUNDED" : "NOT_PAID");
        }
        caseRepository.save(approvalCase);
    }

    /** Opens a child case that renews an expiring permit, carrying the checklist across. */
    @Transactional
    public ApprovalCaseDetailResponse renew(UUID caseUuid) {
        AuthPrincipal principal = requireStaff();
        UUID companyId = requireCompany();
        ApprovalCase source = requireCase(caseUuid, companyId);
        if (!source.getStatus().isActivePermit() && source.getStatus() != ApprovalCaseStatus.EXPIRED) {
            throw new BadRequestException("Only a live or expired permit can be renewed");
        }

        ApprovalCase renewal = new ApprovalCase();
        renewal.setProjectId(source.getProjectId());
        renewal.setCompanyId(companyId);
        renewal.setPermitTypeCode(source.getPermitTypeCode());
        renewal.setPermitTypeName(source.getPermitTypeName());
        renewal.setAuthorityCode(source.getAuthorityCode());
        renewal.setAuthorityName(source.getAuthorityName());
        renewal.setCaseNumber(caseNumberGenerator.next(companyId, source.getPermitTypeCode()));
        renewal.setStatus(ApprovalCaseStatus.NOT_STARTED);
        renewal.setSlaDays(source.getSlaDays());
        renewal.setAssignedToAccountId(source.getAssignedToAccountId());
        renewal.setPrerequisitePermitCodes(source.getPrerequisitePermitCodes());
        renewal.setBlocksActivityCodes(source.getBlocksActivityCodes());
        renewal.setLinkedActivityUuids(source.getLinkedActivityUuids());
        renewal.setRenewalOfCaseUuid(source.getUuid());
        renewal.setTargetSubmissionDate(source.getExpiryDate() != null
                ? source.getExpiryDate().minusDays(source.getSlaDays() != null ? source.getSlaDays() : 14)
                : LocalDate.now());
        renewal.setCreatedBy(principal.getAccountId());
        caseRepository.save(renewal);

        // Copy the checklist so the PRO does not restart from an empty list.
        int order = 0;
        for (CaseChecklistItem source0 : checklistRepository
                .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(source.getUuid())) {
            CaseChecklistItem copy = new CaseChecklistItem();
            copy.setCaseUuid(renewal.getUuid());
            copy.setDocumentTypeCode(source0.getDocumentTypeCode());
            copy.setDocumentTypeName(source0.getDocumentTypeName());
            copy.setRequired(source0.isRequired());
            copy.setStatus("MISSING");
            copy.setSortOrder(order++);
            checklistRepository.save(copy);
        }

        ApprovalCaseStatus from = source.getStatus();
        if (from.canMoveTo(ApprovalCaseStatus.RENEWAL_IN_PROGRESS)) {
            source.setStatus(ApprovalCaseStatus.RENEWAL_IN_PROGRESS);
            caseRepository.save(source);
            recordEvent(source, from, ApprovalCaseStatus.RENEWAL_IN_PROGRESS, "RENEWAL_OPENED",
                    "Renewal case " + renewal.getCaseNumber(), principal.getAccountId());
        }
        recordEvent(renewal, null, ApprovalCaseStatus.NOT_STARTED, "RENEWAL_OF",
                source.getCaseNumber(), principal.getAccountId());

        return get(renewal.getUuid());
    }

    // Deposits and analytics

    @Transactional(readOnly = true)
    public List<CaseFeeResponse> deposits(boolean outstandingOnly) {
        requireStaff();
        UUID companyId = requireCompany();
        List<CaseFee> fees = outstandingOnly
                ? feeRepository.findOutstandingDeposits(companyId)
                : feeRepository.findAllDeposits(companyId);

        Map<UUID, ApprovalCase> cases = new LinkedHashMap<>();
        for (ApprovalCase c : caseRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            cases.put(c.getUuid(), c);
        }
        Map<Long, String> projects = projectNames(companyId);
        return fees.stream().map(f -> toFee(f, cases.get(f.getCaseUuid()), projects)).toList();
    }

    /** Observed turnaround per authority against the seeded estimate. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> slaAnalytics() {
        requireStaff();
        UUID companyId = requireCompany();

        Map<String, List<Long>> actualsByPermit = new LinkedHashMap<>();
        for (ApprovalCase c : caseRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            if (c.getSubmittedDate() == null || c.getApprovedDate() == null) continue;
            actualsByPermit
                    .computeIfAbsent(c.getPermitTypeCode(), k -> new ArrayList<>())
                    .add(workingDaysBetween(c.getSubmittedDate(), c.getApprovedDate()));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, PermitType> permits = libraryService.permitTypeIndex(companyId);
        for (Map.Entry<String, List<Long>> entry : actualsByPermit.entrySet()) {
            PermitType permit = permits.get(entry.getKey());
            List<Long> values = new ArrayList<>(entry.getValue());
            values.sort(Long::compareTo);
            long median = values.get(values.size() / 2);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("permitTypeCode", entry.getKey());
            row.put("permitTypeName", permit != null ? permit.getName() : entry.getKey());
            row.put("authorityCode", permit != null ? permit.getAuthorityCode() : null);
            row.put("indicativeSlaRaw", permit != null ? permit.getIndicativeSlaRaw() : null);
            row.put("indicativeSlaDaysMax", permit != null ? permit.getIndicativeSlaDaysMax() : null);
            row.put("actualMedianDays", median);
            row.put("sampleSize", values.size());
            row.put("usingActual", values.size() >= ACTUAL_SLA_MIN_SAMPLE);
            row.put("fastest", values.get(0));
            row.put("slowest", values.get(values.size() - 1));
            out.add(row);
        }
        return out;
    }

    // Scheduled sweeps, called by ApprovalAlertScheduler

    /**
     * Alerts on permits approaching expiry at 30, 14, 7 and 1 days, moves them to
     * EXPIRING_SOON, and marks lapsed permits EXPIRED.
     *
     * @return how many alerts were raised
     */
    @Transactional
    public int sweepExpiries() {
        LocalDate today = LocalDate.now();
        int raised = 0;

        for (ApprovalCase approvalCase : caseRepository.findExpiringOnOrBefore(today.plusDays(30))) {
            long daysLeft = ChronoUnit.DAYS.between(today, approvalCase.getExpiryDate());

            if (daysLeft < 0) {
                if (approvalCase.getStatus() != ApprovalCaseStatus.EXPIRED) {
                    ApprovalCaseStatus from = approvalCase.getStatus();
                    approvalCase.setStatus(ApprovalCaseStatus.EXPIRED);
                    caseRepository.save(approvalCase);
                    recordEvent(approvalCase, from, ApprovalCaseStatus.EXPIRED, "EXPIRED", null, null);
                    // A lapsed permit stops the linked work, so the programme is re-solved
                    // during the sweep rather than the next time someone opens the Gantt.
                    syncSchedule(approvalCase, approvalCase.getCompanyId());
                }
                if (raiseExpiryAlert(approvalCase, daysLeft, "CRITICAL",
                        approvalCase.getPermitTypeName() + " has expired",
                        "Permit " + approvalCase.getPermitNumber() + " expired on " + approvalCase.getExpiryDate()
                                + ". Work on the linked activities is blocked until it is renewed.")) {
                    raised++;
                }
                continue;
            }

            if (approvalCase.getStatus() == ApprovalCaseStatus.ISSUED && daysLeft <= 30) {
                approvalCase.setStatus(ApprovalCaseStatus.EXPIRING_SOON);
                caseRepository.save(approvalCase);
                recordEvent(approvalCase, ApprovalCaseStatus.ISSUED, ApprovalCaseStatus.EXPIRING_SOON,
                        "EXPIRING_SOON", daysLeft + " days left", null);
            }

            for (int threshold : EXPIRY_ALERT_THRESHOLDS) {
                if (daysLeft == threshold && raiseExpiryAlert(approvalCase, daysLeft, threshold <= 7 ? "WARNING" : "INFO",
                        approvalCase.getPermitTypeName() + " expires in " + threshold + " days",
                        "Permit " + approvalCase.getPermitNumber() + " for case " + approvalCase.getCaseNumber()
                                + " expires on " + approvalCase.getExpiryDate() + ".")) {
                    raised++;
                }
            }
        }
        return raised;
    }

    private boolean raiseExpiryAlert(ApprovalCase approvalCase, long daysLeft, String severity,
                                     String title, String body) {
        return notificationService.raise(new NotificationService.Alert(
                approvalCase.getCompanyId(),
                approvalCase.getAssignedToAccountId(),
                "APPROVAL_EXPIRY",
                severity,
                title,
                body,
                "/admin/projects/" + approvalCase.getProjectId() + "/approvals",
                "APPROVAL_CASE",
                approvalCase.getUuid(),
                "expiry:" + approvalCase.getUuid() + ":" + daysLeft,
                true));
    }

    /**
     * Escalates cases where the authority has blown its SLA. The PM hears about it at the due
     * date and the director three days later.
     */
    @Transactional
    public int sweepSlaBreaches() {
        LocalDate today = LocalDate.now();
        int raised = 0;
        for (ApprovalCase approvalCase : caseRepository.findOverdueSla(today)) {
            long daysLate = ChronoUnit.DAYS.between(approvalCase.getSlaDueDate(), today);
            boolean director = daysLate >= 3;
            boolean created = notificationService.raise(new NotificationService.Alert(
                    approvalCase.getCompanyId(),
                    approvalCase.getAssignedToAccountId(),
                    "APPROVAL_SLA",
                    director ? "CRITICAL" : "WARNING",
                    approvalCase.getPermitTypeName() + " is " + daysLate + " days past its SLA",
                    "Case " + approvalCase.getCaseNumber() + " was submitted on " + approvalCase.getSubmittedDate()
                            + " and was due back on " + approvalCase.getSlaDueDate() + ". No outcome recorded."
                            + (director ? " Escalating to the director." : ""),
                    "/admin/projects/" + approvalCase.getProjectId() + "/approvals",
                    "APPROVAL_CASE",
                    approvalCase.getUuid(),
                    "sla:" + approvalCase.getUuid() + ":" + (director ? "director" : "pm"),
                    true));
            if (created) {
                raised++;
                if (director && approvalCase.getEscalatedAt() == null) {
                    approvalCase.setEscalatedAt(java.time.OffsetDateTime.now());
                    caseRepository.save(approvalCase);
                }
            }
        }
        return raised;
    }

    /** Alerts finance when a deposit has been outstanding more than 60 days after completion. */
    @Transactional
    public int sweepStaleDeposits() {
        int raised = 0;
        for (Project project : projectRepository.findAll()) {
            if (project.getCompanyId() == null) continue;
            for (CaseFee fee : feeRepository.findOutstandingDeposits(project.getCompanyId())) {
                if (!fee.getProjectId().equals(project.getId()) || fee.getPaidDate() == null) continue;
                long days = ChronoUnit.DAYS.between(fee.getPaidDate(), LocalDate.now());
                if (days < 60) continue;
                if (notificationService.raise(new NotificationService.Alert(
                        fee.getCompanyId(), null, "DEPOSIT_OUTSTANDING", "WARNING",
                        "Deposit outstanding " + days + " days",
                        fee.getCurrency() + " " + fee.getAmount() + " paid on " + fee.getPaidDate()
                                + " for " + project.getName() + " has not been refunded.",
                        "/admin/approvals/deposits", "CASE_FEE", fee.getUuid(),
                        "deposit:" + fee.getUuid() + ":" + (days / 30), false))) {
                    raised++;
                }
            }
        }
        return raised;
    }

    // Mapping

    private List<ApprovalCaseResponse> toResponses(List<ApprovalCase> cases, UUID companyId) {
        Map<Long, String> projects = projectNames(companyId);
        Map<Long, String> accounts = accountNames();
        List<ApprovalCaseResponse> out = new ArrayList<>();
        for (ApprovalCase approvalCase : cases) {
            List<CaseChecklistItem> checklist = checklistRepository
                    .findByCaseUuidOrderBySortOrderAscDocumentTypeCodeAsc(approvalCase.getUuid());
            out.add(toResponse(approvalCase, checklist, companyId, projects, accounts));
        }
        return out;
    }

    private ApprovalCaseResponse toResponse(ApprovalCase c, List<CaseChecklistItem> checklist,
                                            UUID companyId, Map<Long, String> projects,
                                            Map<Long, String> accounts) {
        checklist.forEach(CaseChecklistItem::refreshStatus);
        int blocking = (int) checklist.stream().filter(CaseChecklistItem::isBlocking).count();
        List<String> unmet = unmetPrerequisites(c, companyId);
        List<String> blockingCompanyDocs = libraryService.blockingComplianceCodes(companyId,
                checklist.stream()
                        .filter(CaseChecklistItem::isRequired)
                        .map(CaseChecklistItem::getDocumentTypeCode)
                        .toList());

        boolean ready = blocking == 0 && unmet.isEmpty();
        String blockReason = null;
        if (blocking > 0) {
            blockReason = blocking + " required document" + (blocking == 1 ? " is" : "s are") + " missing or expired";
        } else if (!unmet.isEmpty()) {
            blockReason = "Waiting on " + String.join(", ", unmet);
        }

        return ApprovalCaseResponse.builder()
                .uuid(c.getUuid())
                .projectId(c.getProjectId())
                .projectName(projects.get(c.getProjectId()))
                .caseNumber(c.getCaseNumber())
                .permitTypeCode(c.getPermitTypeCode())
                .permitTypeName(c.getPermitTypeName())
                .authorityCode(c.getAuthorityCode())
                .authorityName(c.getAuthorityName())
                .status(c.getStatus().name())
                .assignedToAccountId(c.getAssignedToAccountId())
                .assignedToName(accounts.get(c.getAssignedToAccountId()))
                .targetSubmissionDate(c.getTargetSubmissionDate())
                .submittedDate(c.getSubmittedDate())
                .authorityReference(c.getAuthorityReference())
                .slaDueDate(c.getSlaDueDate())
                .slaDays(c.getSlaDays())
                .daysToSlaDue(c.daysToSlaDue())
                .approvedDate(c.getApprovedDate())
                .permitNumber(c.getPermitNumber())
                .permitFilePath(c.getPermitFilePath())
                .issueDate(c.getIssueDate())
                .expiryDate(c.getExpiryDate())
                .daysToExpiry(c.daysToExpiry())
                .feePaid(c.getFeePaid())
                .depositPaid(c.getDepositPaid())
                .depositStatus(c.getDepositStatus())
                .currentVersion(c.getCurrentVersion())
                .prerequisitePermitCodes(SeedValueParser.splitList(c.getPrerequisitePermitCodes()))
                .blocksActivityCodes(SeedValueParser.splitList(c.getBlocksActivityCodes()))
                .linkedActivityUuids(parseUuids(c.getLinkedActivityUuids()))
                .notes(c.getNotes())
                .blockingChecklistCount(blocking)
                .unmetPrerequisites(unmet)
                .blockingCompanyDocuments(blockingCompanyDocs)
                .readyToSubmit(ready)
                .blockReason(blockReason)
                .build();
    }

    private CaseChecklistItemResponse toChecklistItem(CaseChecklistItem item, Map<String, DocumentType> types) {
        item.refreshStatus();
        DocumentType type = types.get(item.getDocumentTypeCode());
        return CaseChecklistItemResponse.builder()
                .uuid(item.getUuid())
                .documentTypeCode(item.getDocumentTypeCode())
                .documentTypeName(item.getDocumentTypeName())
                .category(type != null ? type.getCategory() : null)
                .required(item.isRequired())
                .status(item.getStatus())
                .filePath(item.getFilePath())
                .source(item.getSource())
                .expiryDate(item.getExpiryDate())
                .daysToExpiry(item.getExpiryDate() != null
                        ? ChronoUnit.DAYS.between(LocalDate.now(), item.getExpiryDate()) : null)
                .waivedBy(item.getWaivedBy())
                .waiverReason(item.getWaiverReason())
                .blocking(item.isBlocking())
                .build();
    }

    private CaseSubmissionResponse toSubmission(CaseSubmission s) {
        return CaseSubmissionResponse.builder()
                .uuid(s.getUuid())
                .version(s.getVersion())
                .submittedBy(s.getSubmittedBy())
                .submittedDate(s.getSubmittedDate())
                .channel(s.getChannel())
                .receiptFilePath(s.getReceiptFilePath())
                .feeAmount(s.getFeeAmount())
                .outcome(s.getOutcome())
                .outcomeDate(s.getOutcomeDate())
                .turnaroundDays(s.getTurnaroundDays())
                .notes(s.getNotes())
                .build();
    }

    private CaseCommentResponse toComment(CaseComment c) {
        return CaseCommentResponse.builder()
                .uuid(c.getUuid())
                .submissionUuid(c.getSubmissionUuid())
                .commentText(c.getCommentText())
                .reasonCode(c.getReasonCode())
                .raisedDate(c.getRaisedDate())
                .respondedDate(c.getRespondedDate())
                .responseText(c.getResponseText())
                .responsibleParty(c.getResponsibleParty())
                .build();
    }

    private CaseFeeResponse toFee(CaseFee f, ApprovalCase approvalCase, Map<Long, String> projects) {
        Long outstanding = null;
        if (f.isOutstandingDeposit()) {
            outstanding = ChronoUnit.DAYS.between(f.getPaidDate(), LocalDate.now());
        }
        return CaseFeeResponse.builder()
                .uuid(f.getUuid())
                .caseUuid(f.getCaseUuid())
                .projectId(f.getProjectId())
                .projectName(projects != null ? projects.get(f.getProjectId()) : null)
                .caseNumber(approvalCase != null ? approvalCase.getCaseNumber() : null)
                .permitTypeName(approvalCase != null ? approvalCase.getPermitTypeName() : null)
                .authorityName(approvalCase != null ? approvalCase.getAuthorityName() : null)
                .type(f.getType())
                .amount(f.getAmount())
                .currency(f.getCurrency())
                .paidDate(f.getPaidDate())
                .paymentRef(f.getPaymentRef())
                .receiptFilePath(f.getReceiptFilePath())
                .costCode(f.getCostCode())
                .refundable(f.isRefundable())
                .refundClaimedDate(f.getRefundClaimedDate())
                .refundReceivedDate(f.getRefundReceivedDate())
                .refundAmount(f.getRefundAmount())
                .notes(f.getNotes())
                .daysOutstanding(outstanding)
                .build();
    }

    private CaseEventResponse toEvent(ApprovalCaseEvent e) {
        return CaseEventResponse.builder()
                .uuid(e.getUuid())
                .fromStatus(e.getFromStatus())
                .toStatus(e.getToStatus())
                .action(e.getAction())
                .detail(e.getDetail())
                .actorAccountId(e.getActorAccountId())
                .createdAt(e.getCreatedAt())
                .build();
    }

    // Helpers

    /** Location and scope for a resolve, taking overrides ahead of what the project stores. */
    private record Location(String emirate, String community, String building, String plotZone,
                            ProjectScopeToggles scope, boolean newBuild) {
    }

    private Location resolveLocation(Project project, ApprovalResolveRequest request) {
        String emirate = firstNonBlank(request != null ? request.getEmirate() : null, project.getEmirate());
        String community = firstNonBlank(request != null ? request.getCommunityName() : null, project.getCommunityName());
        String building = firstNonBlank(request != null ? request.getBuildingName() : null, project.getBuildingName());
        String plotZone = firstNonBlank(request != null ? request.getPlotZone() : null, project.getPlotZone());

        ProjectScopeToggles scope = request != null && request.getScope() != null
                ? request.getScope()
                : readScope(project);

        boolean newBuild = project.getProjectType() != null
                && project.getProjectType().toLowerCase(Locale.ROOT).contains("new build");
        return new Location(emirate, community, building, plotZone, scope, newBuild);
    }

    private ProjectScopeToggles readScope(Project project) {
        if (!StringUtils.hasText(project.getScopeTogglesJson())) {
            return ProjectScopeToggles.defaults();
        }
        try {
            return objectMapper.readValue(project.getScopeTogglesJson(), ProjectScopeToggles.class);
        } catch (Exception e) {
            return ProjectScopeToggles.defaults();
        }
    }

    private void persistLocation(Project project, Location location) {
        project.setEmirate(location.emirate());
        project.setCommunityName(location.community());
        project.setBuildingName(location.building());
        project.setPlotZone(location.plotZone());
        try {
            project.setScopeTogglesJson(objectMapper.writeValueAsString(location.scope()));
        } catch (Exception ignored) {
            // Scope stays as it was; the resolve still runs with the in-memory value.
        }
        projectRepository.save(project);
    }

    private void recordEvent(ApprovalCase approvalCase, ApprovalCaseStatus from, ApprovalCaseStatus to,
                             String action, String detail, Long actorAccountId) {
        ApprovalCaseEvent event = new ApprovalCaseEvent();
        event.setCaseUuid(approvalCase.getUuid());
        event.setFromStatus(from != null ? from.name() : null);
        event.setToStatus(to != null ? to.name() : null);
        event.setAction(action);
        event.setDetail(detail);
        event.setActorAccountId(actorAccountId);
        eventRepository.save(event);
    }

    /**
     * Adds working days on the default UAE week (Saturday to Thursday).
     * Authority SLAs are quoted in working days, so a 15-day SLA submitted on a Wednesday is
     * not due 15 calendar days later.
     */
    static LocalDate addWorkingDays(LocalDate from, int workingDays) {
        LocalDate date = from;
        int added = 0;
        while (added < workingDays) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != java.time.DayOfWeek.FRIDAY) {
                added++;
            }
        }
        return date;
    }

    static long workingDaysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return 0;
        long days = 0;
        LocalDate cursor = from;
        while (cursor.isBefore(to)) {
            cursor = cursor.plusDays(1);
            if (cursor.getDayOfWeek() != java.time.DayOfWeek.FRIDAY) days++;
        }
        return days;
    }

    private List<UUID> parseUuids(String csv) {
        List<UUID> out = new ArrayList<>();
        for (String value : SeedValueParser.splitList(csv)) {
            try {
                out.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // A malformed id in legacy data should not break the whole response.
            }
        }
        return out;
    }

    private Map<Long, String> projectNames(UUID companyId) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Project project : projectRepository.findByCompanyIdAndIsDeletedFalse(companyId)) {
            names.put(project.getId(), project.getName());
        }
        return names;
    }

    private Map<Long, String> accountNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Account account : accountRepository.findAll()) {
            names.put(account.getId(), account.getFullName() != null ? account.getFullName() : account.getEmail());
        }
        return names;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private ApprovalCase requireCase(UUID uuid, UUID companyId) {
        return caseRepository.findByUuidAndCompanyId(uuid, companyId)
                .orElseThrow(() -> new NotFoundException("Approval case not found"));
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

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requireDirector() {
        AuthPrincipal principal = requireStaff();
        if (principal.getRoles() == null || !(
                principal.getRoles().contains(Role.BUSINESS_OWNER)
                        || principal.getRoles().contains(Role.ADMIN)
                        || principal.getRoles().contains(Role.SUPER_ADMIN))) {
            throw new ForbiddenException("Only a director can waive a required document");
        }
        return principal;
    }
}
