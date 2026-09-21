package com.fitouts.variation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.commercialapproval.api.ApprovalRunResponse;
import com.fitouts.commercialapproval.application.CommercialApprovalCompletionHandler;
import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.CommercialApprovalRun;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.variation.api.ProjectCommercialResponse;
import com.fitouts.variation.api.VariationDecisionRequest;
import com.fitouts.variation.api.VariationBoqChangeResponse;
import com.fitouts.variation.api.VariationResponse;
import com.fitouts.variation.api.VariationTriageRequest;
import com.fitouts.variation.api.VariationUpsertRequest;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.variation.domain.VariationAttachment;
import com.fitouts.variation.domain.VariationAttachmentRepository;
import com.fitouts.variation.domain.VariationBoqChangeRepository;
import com.fitouts.variation.domain.VariationCostMode;
import com.fitouts.variation.domain.VariationEvent;
import com.fitouts.variation.domain.VariationEventRepository;
import com.fitouts.variation.domain.VariationLine;
import com.fitouts.variation.domain.VariationLineRepository;
import com.fitouts.variation.domain.VariationLineType;
import com.fitouts.variation.domain.VariationLink;
import com.fitouts.variation.domain.VariationLinkRepository;
import com.fitouts.variation.domain.VariationLinkType;
import com.fitouts.variation.domain.VariationOrigin;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VariationService implements CommercialApprovalCompletionHandler {

    private static final Set<Role> STAFF_RAISE = EnumSet.of(
            Role.PROJECT_MANAGER, Role.QS, Role.SENIOR_QS, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> TRIAGE_ROLES = EnumSet.of(
            Role.PROJECT_MANAGER, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> STAFF = EnumSet.of(
            Role.ADMIN, Role.SUPER_ADMIN, Role.BUSINESS_OWNER, Role.PROJECT_MANAGER,
            Role.QS, Role.SENIOR_QS, Role.FINANCE);
    private static final Set<VariationStatus> EDITABLE = EnumSet.of(
            VariationStatus.DRAFT, VariationStatus.REVISED, VariationStatus.AWAITING_TRIAGE);

    private final VariationRequestRepository variationRepository;
    private final VariationLineRepository lineRepository;
    private final VariationLinkRepository linkRepository;
    private final VariationAttachmentRepository attachmentRepository;
    private final VariationEventRepository eventRepository;
    private final ProjectCommercialRepository commercialRepository;
    private final ProjectService projectService;
    private final BoqProjectRules boqProjectRules;
    private final BoqLineRepository boqLineRepository;
    private final WorkItemRepository workItemRepository;
    private final FileStorageService fileStorageService;
    private final CommercialApprovalService commercialApprovalService;
    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final VariationRebaselineService variationRebaselineService;
    private final VariationBoqApplyService variationBoqApplyService;
    private final VariationBoqChangeRepository variationBoqChangeRepository;

    @Override
    public CommercialEventType supports() {
        return CommercialEventType.VARIATION;
    }

    @Override
    @Transactional
    public void onApproved(UUID entityUuid, UUID runUuid) {
        VariationRequest vr = variationRepository.findById(entityUuid)
                .orElseThrow(() -> new NotFoundException("Variation not found"));
        if (vr.getStatus() != VariationStatus.INTERNAL_REVIEW) {
            return;
        }
        VariationStatus from = vr.getStatus();
        vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
        vr.setIssuedBy(resolveMatrixIssuer(runUuid));
        vr.setIssuedAt(OffsetDateTime.now());
        vr.setApprovalRunUuid(runUuid);
        variationRepository.save(vr);
        appendEvent(vr, "ISSUED", from.name(), VariationStatus.ISSUED_TO_CLIENT.name(), null, "Matrix approved");
        notifyClient(vr, "VARIATION_PENDING", "Variation awaiting your approval",
                vr.getCrNumber() + " is ready for client approval.",
                "/client/variations");
    }

    @Override
    @Transactional
    public void onRejected(UUID entityUuid, UUID runUuid, String comment) {
        VariationRequest vr = variationRepository.findById(entityUuid)
                .orElseThrow(() -> new NotFoundException("Variation not found"));
        if (vr.getStatus() != VariationStatus.INTERNAL_REVIEW) {
            return;
        }
        VariationStatus from = vr.getStatus();
        vr.setStatus(VariationStatus.REVISED);
        vr.setRejectComment(comment);
        vr.setApprovalRunUuid(runUuid);
        variationRepository.save(vr);
        appendEvent(vr, "INTERNAL_REJECTED", from.name(), VariationStatus.REVISED.name(), null, comment);
        if (vr.getSubmittedBy() != null) {
            raiseAlert(vr, vr.getSubmittedBy(), "VARIATION_REJECTED", "WARNING",
                    "Variation returned: " + vr.getCrNumber(),
                    comment != null ? comment : "Internal review rejected",
                    "/admin/projects/" + vr.getProjectId() + "/variations/" + vr.getUuid(),
                    "vr-rejected:" + vr.getUuid() + ":" + System.currentTimeMillis());
        }
    }

    @Transactional(readOnly = true)
    public List<VariationResponse> listForProject(Long projectId) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        if (isPureClient(principal)) {
            assertClientOwns(project, principal);
        } else {
            requireStaff();
        }
        return variationRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), CompanyContext.get())
                .stream()
                .filter(v -> !isPureClient(principal) || clientVisible(v))
                .map(v -> toResponse(v, project, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public VariationResponse get(Long projectId, UUID uuid) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (isPureClient(principal)) {
            assertClientOwns(project, principal);
            if (!clientVisible(vr)) {
                throw new ForbiddenException("Variation not visible to client");
            }
        } else {
            requireStaff();
        }
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse create(Long projectId, VariationUpsertRequest request) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        boolean byClient = isPureClient(principal);
        if (byClient) {
            assertClientOwns(project, principal);
        } else if (!hasAnyRole(principal, STAFF_RAISE)) {
            throw new ForbiddenException("Cannot raise variations");
        }
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new BadRequestException("title is required");
        }

        VariationRequest vr = new VariationRequest();
        vr.setProjectId(project.getId());
        vr.setCompanyId(CompanyContext.get());
        vr.setCrNumber(nextCrNumber(project.getId()));
        vr.setTitle(request.getTitle().trim());
        vr.setDescription(request.getDescription());
        vr.setReasonCode(request.getReasonCode());
        vr.setCostMode(request.getCostMode() != null ? request.getCostMode() : VariationCostMode.LUMP_SUM);
        vr.setProposedDelayDays(request.getProposedDelayDays());
        vr.setApplyScheduleOnApproval(Boolean.TRUE.equals(request.getApplyScheduleOnApproval()));
        vr.setRaisedBy(principal.getAccountId());

        if (byClient) {
            vr.setOrigin(VariationOrigin.CLIENT);
            vr.setStatus(VariationStatus.AWAITING_TRIAGE);
        } else {
            vr.setOrigin(resolveStaffOrigin(principal));
            vr.setStatus(VariationStatus.DRAFT);
        }
        vr = variationRepository.save(vr);

        if (!byClient) {
            replaceLinesAndLinks(vr, request);
            recalcTotals(vr);
        } else if (request.getLinks() != null) {
            replaceLinks(vr, request.getLinks());
        }

        appendEvent(vr, "CREATED", null, vr.getStatus().name(), principal.getAccountId(), null);
        if (byClient) {
            notifyPms(vr, "VARIATION_TRIAGE", "Client variation needs triage",
                    vr.getCrNumber() + ": " + vr.getTitle(),
                    "/admin/variations/inbox");
        }
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse update(Long projectId, UUID uuid, VariationUpsertRequest request) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        assertEditable(vr);
        boolean byClient = isPureClient(principal);
        if (byClient) {
            assertClientOwns(project, principal);
            if (vr.getOrigin() != VariationOrigin.CLIENT || vr.getStatus() != VariationStatus.AWAITING_TRIAGE) {
                throw new ForbiddenException("Client can only edit awaiting-triage requests");
            }
            if (request != null) {
                if (StringUtils.hasText(request.getTitle())) vr.setTitle(request.getTitle().trim());
                if (request.getDescription() != null) vr.setDescription(request.getDescription());
                if (request.getReasonCode() != null) vr.setReasonCode(request.getReasonCode());
                if (request.getLinks() != null) replaceLinks(vr, request.getLinks());
            }
        } else {
            requireStaff();
            if (request != null) {
                if (StringUtils.hasText(request.getTitle())) vr.setTitle(request.getTitle().trim());
                if (request.getDescription() != null) vr.setDescription(request.getDescription());
                if (request.getReasonCode() != null) vr.setReasonCode(request.getReasonCode());
                if (request.getCostMode() != null) vr.setCostMode(request.getCostMode());
                if (request.getProposedDelayDays() != null) vr.setProposedDelayDays(request.getProposedDelayDays());
                if (request.getApplyScheduleOnApproval() != null) {
                    vr.setApplyScheduleOnApproval(request.getApplyScheduleOnApproval());
                }
                replaceLinesAndLinks(vr, request);
                recalcTotals(vr);
            }
        }
        variationRepository.save(vr);
        appendEvent(vr, "UPDATED", vr.getStatus().name(), vr.getStatus().name(),
                principal.getAccountId(), null);
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse triage(Long projectId, UUID uuid, VariationTriageRequest request) {
        AuthPrincipal principal = requireStaff(TRIAGE_ROLES);
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (vr.getStatus() != VariationStatus.AWAITING_TRIAGE) {
            throw new BadRequestException("Variation is not awaiting triage");
        }
        if (request == null) {
            throw new BadRequestException("triage body required");
        }
        VariationStatus from = vr.getStatus();
        vr.setTriageNote(trimToNull(request.getNote()));
        if (request.isAccept()) {
            vr.setStatus(VariationStatus.DRAFT);
            appendEvent(vr, "TRIAGE_ACCEPTED", from.name(), VariationStatus.DRAFT.name(),
                    principal.getAccountId(), request.getNote());
        } else {
            vr.setStatus(VariationStatus.REJECTED);
            vr.setRejectComment(trimToNull(request.getNote()));
            appendEvent(vr, "TRIAGE_REJECTED", from.name(), VariationStatus.REJECTED.name(),
                    principal.getAccountId(), request.getNote());
        }
        variationRepository.save(vr);
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse submitReview(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff(STAFF_RAISE);
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (vr.getStatus() != VariationStatus.DRAFT && vr.getStatus() != VariationStatus.REVISED) {
            throw new BadRequestException("Only DRAFT or REVISED can be submitted for review");
        }
        recalcTotals(vr);
        if (vr.getSellDelta() == null) {
            vr.setSellDelta(BigDecimal.ZERO);
        }

        VariationStatus from = vr.getStatus();
        Optional<CommercialApprovalRun> run = commercialApprovalService.startRun(
                CommercialEventType.VARIATION,
                vr.getUuid(),
                vr.getProjectId(),
                vr.getSellDelta(),
                principal.getAccountId());

        if (run.isEmpty()) {
            // No matrix → issue directly
            vr.setStatus(VariationStatus.ISSUED_TO_CLIENT);
            vr.setSubmittedBy(principal.getAccountId());
            vr.setSubmittedAt(OffsetDateTime.now());
            vr.setIssuedBy(principal.getAccountId());
            vr.setIssuedAt(OffsetDateTime.now());
            variationRepository.save(vr);
            appendEvent(vr, "ISSUED", from.name(), VariationStatus.ISSUED_TO_CLIENT.name(),
                    principal.getAccountId(), "No matrix — auto issued");
            notifyClient(vr, "VARIATION_PENDING", "Variation awaiting your approval",
                    vr.getCrNumber() + " is ready for client approval.",
                    "/client/variations");
            return toResponse(vr, project, true);
        }

        vr.setStatus(VariationStatus.INTERNAL_REVIEW);
        vr.setSubmittedBy(principal.getAccountId());
        vr.setSubmittedAt(OffsetDateTime.now());
        vr.setApprovalRunUuid(run.get().getUuid());
        vr.setRejectComment(null);
        variationRepository.save(vr);
        appendEvent(vr, "SUBMITTED_REVIEW", from.name(), VariationStatus.INTERNAL_REVIEW.name(),
                principal.getAccountId(), "Approval run " + run.get().getUuid());
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse clientApprove(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireClient();
        Project project = requireProject(projectId);
        assertClientOwns(project, principal);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (vr.getStatus() != VariationStatus.ISSUED_TO_CLIENT) {
            throw new BadRequestException("Variation is not issued to client");
        }

        ProjectCommercial commercial = ensureCommercial(project);
        BigDecimal previousContract = commercial.getCurrentContractValue();
        BigDecimal previousMargin = commercial.getCurrentMargin();
        BigDecimal newContract = previousContract.add(nullSafe(vr.getSellDelta()));
        BigDecimal previousCost = commercial.getCurrentCost() != null
                ? commercial.getCurrentCost() : BigDecimal.ZERO;
        BigDecimal newCost = previousCost.add(nullSafe(vr.getCostDelta()));
        BigDecimal newMargin = newContract.subtract(newCost);

        commercial.setCurrentContractValue(newContract);
        commercial.setCurrentCost(newCost);
        commercial.setCurrentMargin(newMargin);
        commercialRepository.save(commercial);

        VariationStatus from = vr.getStatus();
        vr.setStatus(VariationStatus.APPROVED);
        vr.setApprovedBy(principal.getAccountId());
        vr.setApprovedAt(OffsetDateTime.now());
        vr.setLockedAt(OffsetDateTime.now());
        variationRepository.save(vr);

        VariationRebaselineService.RebaselineResult rebaselineResult =
                variationRebaselineService.rebaseline(projectId, vr, principal.getAccountId());
        try {
            UUID resultBoqId = variationBoqApplyService.apply(vr, principal.getAccountId());
            appendEvent(vr, "BOQ_APPLIED", VariationStatus.APPROVED.name(),
                    VariationStatus.APPROVED.name(), principal.getAccountId(),
                    "Created approved BOQ revision " + resultBoqId);
        } catch (BadRequestException e) {
            String action = e.getMessage() != null && e.getMessage().contains("No approved BOQ")
                    ? "BOQ_SKIPPED_NO_APPROVED" : "BOQ_APPLY_FAILED";
            log.warn("Could not apply variation {} to BOQ: {}", vr.getUuid(), e.getMessage());
            appendEvent(vr, action, VariationStatus.APPROVED.name(),
                    VariationStatus.APPROVED.name(), principal.getAccountId(), e.getMessage());
        } catch (Exception e) {
            log.warn("Could not apply variation {} to BOQ: {}", vr.getUuid(), e.getMessage());
            appendEvent(vr, "BOQ_APPLY_FAILED", VariationStatus.APPROVED.name(),
                    VariationStatus.APPROVED.name(), principal.getAccountId(), e.getMessage());
        }

        VariationEvent event = new VariationEvent();
        event.setVariationUuid(vr.getUuid());
        event.setAction("CLIENT_APPROVED");
        event.setFromStatus(from.name());
        event.setToStatus(VariationStatus.APPROVED.name());
        event.setActorId(principal.getAccountId());
        event.setDetail(rebaselineResult != null ? rebaselineResult.detailJson() : null);
        event.setPreviousContractValue(previousContract);
        event.setNewContractValue(newContract);
        event.setPreviousMargin(previousMargin);
        event.setNewMargin(newMargin);
        eventRepository.save(event);

        notifyStaff(vr, "VARIATION_APPROVED", "Variation approved: " + vr.getCrNumber(),
                "Client approved. Contract " + previousContract + " → " + newContract,
                "/admin/projects/" + projectId + "/variations/" + vr.getUuid());
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse clientReject(Long projectId, UUID uuid, VariationDecisionRequest request) {
        AuthPrincipal principal = requireClient();
        Project project = requireProject(projectId);
        assertClientOwns(project, principal);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (vr.getStatus() != VariationStatus.ISSUED_TO_CLIENT) {
            throw new BadRequestException("Variation is not issued to client");
        }
        if (request == null || !StringUtils.hasText(request.getComment())) {
            throw new BadRequestException("comment is required");
        }
        VariationStatus from = vr.getStatus();
        vr.setStatus(VariationStatus.REVISED);
        vr.setRejectComment(request.getComment().trim());
        variationRepository.save(vr);
        appendEvent(vr, "CLIENT_REJECTED", from.name(), VariationStatus.REVISED.name(),
                principal.getAccountId(), request.getComment().trim());
        if (vr.getSubmittedBy() != null) {
            raiseAlert(vr, vr.getSubmittedBy(), "VARIATION_REJECTED", "WARNING",
                    "Client rejected: " + vr.getCrNumber(),
                    request.getComment().trim(),
                    "/admin/projects/" + projectId + "/variations/" + vr.getUuid(),
                    "vr-client-reject:" + vr.getUuid() + ":" + System.currentTimeMillis());
        }
        return toResponse(vr, project, true);
    }

    @Transactional
    public VariationResponse uploadAttachment(Long projectId, UUID uuid, MultipartFile file) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        if (isPureClient(principal)) {
            assertClientOwns(project, principal);
            if (vr.getStatus() != VariationStatus.AWAITING_TRIAGE && vr.getStatus() != VariationStatus.DRAFT) {
                throw new BadRequestException("Cannot attach in current status");
            }
        } else {
            requireStaff();
            assertEditable(vr);
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required");
        }
        String path = fileStorageService.store(file, CompanyContext.get(), project.getId(), "variations");
        VariationAttachment att = new VariationAttachment();
        att.setVariationUuid(vr.getUuid());
        att.setFilePath(path);
        att.setOriginalName(file.getOriginalFilename());
        att.setUploadedBy(principal.getAccountId());
        attachmentRepository.save(att);
        return toResponse(vr, project, true);
    }

    @Transactional(readOnly = true)
    public List<VariationResponse> triageInbox() {
        requireStaff(TRIAGE_ROLES);
        return variationRepository
                .findByCompanyIdAndStatusOrderByUpdatedAtDesc(requireCompany(), VariationStatus.AWAITING_TRIAGE)
                .stream()
                .map(v -> toResponse(v, projectService.getById(v.getProjectId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectCommercialResponse getCommercial(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        ProjectCommercial c = ensureCommercial(project);
        return ProjectCommercialResponse.builder()
                .uuid(c.getUuid())
                .projectId(c.getProjectId())
                .originalContractValue(c.getOriginalContractValue())
                .currentContractValue(c.getCurrentContractValue())
                .originalCost(c.getOriginalCost())
                .currentCost(c.getCurrentCost())
                .currentMargin(c.getCurrentMargin())
                .build();
    }

    @Transactional(readOnly = true)
    public List<VariationBoqChangeResponse> getBoqChanges(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        VariationRequest vr = requireVariation(uuid, projectId);
        return variationBoqChangeRepository.findByVariationUuidOrderByCreatedAtAsc(vr.getUuid()).stream()
                .map(c -> VariationBoqChangeResponse.builder()
                        .uuid(c.getUuid())
                        .variationLineUuid(c.getVariationLineUuid())
                        .sourceBoqUuid(c.getSourceBoqUuid())
                        .resultingBoqUuid(c.getResultingBoqUuid())
                        .sourceBoqLineId(c.getSourceBoqLineId())
                        .resultingBoqLineId(c.getResultingBoqLineId())
                        .changeType(c.getChangeType())
                        .description(c.getDescription())
                        .unit(c.getUnit())
                        .previousQuantity(c.getPreviousQuantity())
                        .newQuantity(c.getNewQuantity())
                        .previousRate(c.getPreviousRate())
                        .newRate(c.getNewRate())
                        .previousAmount(c.getPreviousAmount())
                        .newAmount(c.getNewAmount())
                        .deltaAmount(c.getDeltaAmount())
                        .appliedBy(c.getAppliedBy())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(Long projectId, UUID variationUuid, UUID attachmentUuid) {
        AuthPrincipal principal = requirePrincipal();
        Project project = requireProject(projectId);
        VariationRequest vr = requireVariation(variationUuid, projectId);
        if (isPureClient(principal)) {
            assertClientOwns(project, principal);
            if (!clientVisible(vr)) throw new ForbiddenException("Variation not visible to client");
        } else {
            requireStaff();
        }
        VariationAttachment attachment = attachmentRepository
                .findByUuidAndVariationUuid(attachmentUuid, variationUuid)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        return new AttachmentDownload(
                fileStorageService.loadAsResource(attachment.getFilePath()),
                attachment.getOriginalName());
    }

    public record AttachmentDownload(Resource resource, String filename) {}

    // ── Internals ────────────────────────────────────────────────────────────

    private void replaceLinesAndLinks(VariationRequest vr, VariationUpsertRequest request) {
        if (request.getLines() != null) {
            lineRepository.deleteByVariationUuid(vr.getUuid());
            int order = 0;
            for (VariationUpsertRequest.LineRequest lr : request.getLines()) {
                VariationLine line = new VariationLine();
                line.setVariationUuid(vr.getUuid());
                line.setLineType(lr.getLineType() != null ? lr.getLineType() : VariationLineType.LUMP_SUM);
                line.setSourceBoqLineId(lr.getSourceBoqLineId());
                line.setWorkItemId(lr.getWorkItemId());
                line.setDescription(lr.getDescription());
                line.setUnit(lr.getUnit());
                BigDecimal qty = lr.getQuantity() != null ? lr.getQuantity() : BigDecimal.ONE;
                BigDecimal sellRate = lr.getSellRate() != null ? lr.getSellRate() : BigDecimal.ZERO;
                BigDecimal costRate = lr.getCostRate();
                if (costRate == null && lr.getWorkItemId() != null) {
                    costRate = workItemRepository.findById(lr.getWorkItemId())
                            .map(WorkItem::getCostPrice)
                            .orElse(BigDecimal.ZERO);
                    if (costRate == null) costRate = BigDecimal.ZERO;
                }
                if (costRate == null) costRate = BigDecimal.ZERO;
                if (lr.getSourceBoqLineId() != null && line.getLineType() == VariationLineType.MODIFY) {
                    boqLineRepository.findById(lr.getSourceBoqLineId()).ifPresent(bl -> {
                        if (!StringUtils.hasText(line.getDescription())) {
                            line.setDescription(bl.getDescription());
                        }
                        if (!StringUtils.hasText(line.getUnit())) {
                            line.setUnit(bl.getUnit());
                        }
                    });
                }
                line.setQuantity(qty);
                line.setSellRate(sellRate);
                line.setSellAmount(qty.multiply(sellRate).setScale(2, RoundingMode.HALF_UP));
                line.setCostRate(costRate);
                line.setCostAmount(qty.multiply(costRate).setScale(2, RoundingMode.HALF_UP));
                line.setSortOrder(lr.getSortOrder() != null ? lr.getSortOrder() : order++);
                lineRepository.save(line);
            }
        }
        if (request.getLinks() != null) {
            replaceLinks(vr, request.getLinks());
        }
    }

    private void replaceLinks(VariationRequest vr, List<VariationUpsertRequest.LinkRequest> links) {
        linkRepository.deleteByVariationUuid(vr.getUuid());
        for (VariationUpsertRequest.LinkRequest lr : links) {
            VariationLink link = new VariationLink();
            link.setVariationUuid(vr.getUuid());
            link.setLinkType(lr.getLinkType() != null ? lr.getLinkType()
                    : (lr.getRoomId() != null ? VariationLinkType.ROOM : VariationLinkType.ACTIVITY));
            link.setRoomId(lr.getRoomId());
            link.setActivityUuid(lr.getActivityUuid());
            linkRepository.save(link);
        }
    }

    private void recalcTotals(VariationRequest vr) {
        List<VariationLine> lines = lineRepository.findByVariationUuidOrderBySortOrderAsc(vr.getUuid());
        BigDecimal sell = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (VariationLine line : lines) {
            sell = sell.add(nullSafe(line.getSellAmount()));
            cost = cost.add(nullSafe(line.getCostAmount()));
        }
        vr.setSellDelta(sell);
        vr.setCostDelta(cost);
        if (!lines.isEmpty()) {
            boolean hasBoq = lines.stream().anyMatch(l ->
                    l.getLineType() == VariationLineType.NEW || l.getLineType() == VariationLineType.MODIFY);
            boolean hasLump = lines.stream().anyMatch(l -> l.getLineType() == VariationLineType.LUMP_SUM);
            if (hasBoq && hasLump) vr.setCostMode(VariationCostMode.MIXED);
            else if (hasBoq) vr.setCostMode(VariationCostMode.BOQ_LINES);
            else vr.setCostMode(VariationCostMode.LUMP_SUM);
        }
        variationRepository.save(vr);
    }

    private ProjectCommercial ensureCommercial(Project project) {
        return commercialRepository.findByProjectIdAndCompanyId(project.getId(), CompanyContext.get())
                .orElseGet(() -> {
                    BigDecimal base = resolveBaseContract(project);
                    ProjectCommercial c = new ProjectCommercial();
                    c.setProjectId(project.getId());
                    c.setCompanyId(CompanyContext.get());
                    c.setOriginalContractValue(base);
                    c.setCurrentContractValue(base);
                    c.setOriginalCost(null);
                    c.setCurrentCost(BigDecimal.ZERO);
                    c.setCurrentMargin(base);
                    return commercialRepository.save(c);
                });
    }

    private BigDecimal resolveBaseContract(Project project) {
        Optional<BoqDocument> approved = boqProjectRules.findApproved(project.getId());
        if (approved.isPresent() && approved.get().getGrandTotal() != null) {
            return approved.get().getGrandTotal();
        }
        return project.getBudget() != null ? project.getBudget() : BigDecimal.ZERO;
    }

    private String nextCrNumber(Long projectId) {
        long count = variationRepository.countByProject(projectId, requireCompany());
        return String.format("CR-%04d", count + 1);
    }

    private VariationOrigin resolveStaffOrigin(AuthPrincipal principal) {
        if (principal.getRoles() != null && principal.getRoles().contains(Role.PROJECT_MANAGER)) {
            return VariationOrigin.PM;
        }
        return VariationOrigin.QS;
    }

    private void assertEditable(VariationRequest vr) {
        if (vr.getLockedAt() != null || vr.getStatus() == VariationStatus.APPROVED) {
            throw new BadRequestException("Variation is locked; raise a new CR to correct amounts");
        }
        if (!EDITABLE.contains(vr.getStatus()) && vr.getStatus() != VariationStatus.INTERNAL_REVIEW) {
            // Allow editing only editable statuses (not while in internal review — withdraw first)
            if (vr.getStatus() == VariationStatus.INTERNAL_REVIEW) {
                throw new BadRequestException("Withdraw from review before editing (or wait for rejection)");
            }
        }
        if (!EDITABLE.contains(vr.getStatus())) {
            throw new BadRequestException("Variation cannot be edited in status " + vr.getStatus());
        }
    }

    private boolean clientVisible(VariationRequest vr) {
        return vr.getOrigin() == VariationOrigin.CLIENT
                || vr.getStatus() == VariationStatus.ISSUED_TO_CLIENT
                || vr.getStatus() == VariationStatus.APPROVED
                || vr.getStatus() == VariationStatus.REVISED;
    }

    private VariationResponse toResponse(VariationRequest vr, Project project, boolean detail) {
        ProjectCommercial commercial = commercialRepository
                .findByProjectIdAndCompanyId(vr.getProjectId(), vr.getCompanyId())
                .orElse(null);
        BigDecimal currentContract = commercial != null
                ? commercial.getCurrentContractValue()
                : resolveBaseContract(project);
        BigDecimal currentMargin = commercial != null && commercial.getCurrentMargin() != null
                ? commercial.getCurrentMargin()
                : currentContract;
        BigDecimal proposedContract = currentContract.add(nullSafe(vr.getSellDelta()));
        BigDecimal proposedMargin = proposedContract.subtract(
                (commercial != null && commercial.getCurrentCost() != null
                        ? commercial.getCurrentCost() : BigDecimal.ZERO)
                        .add(nullSafe(vr.getCostDelta())));

        ApprovalRunResponse run = null;
        if (detail && vr.getApprovalRunUuid() != null) {
            try {
                run = commercialApprovalService.getRunInternal(vr.getApprovalRunUuid());
            } catch (Exception ignored) {
                // run may belong to cancelled flow
            }
        }

        VariationResponse.VariationResponseBuilder b = VariationResponse.builder()
                .uuid(vr.getUuid())
                .projectId(vr.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .crNumber(vr.getCrNumber())
                .origin(vr.getOrigin())
                .status(vr.getStatus())
                .title(vr.getTitle())
                .description(vr.getDescription())
                .reasonCode(vr.getReasonCode())
                .costMode(vr.getCostMode())
                .sellDelta(vr.getSellDelta())
                .costDelta(vr.getCostDelta())
                .marginDelta(nullSafe(vr.getSellDelta()).subtract(nullSafe(vr.getCostDelta())))
                .proposedDelayDays(vr.getProposedDelayDays())
                .applyScheduleOnApproval(vr.isApplyScheduleOnApproval())
                .lockedAt(vr.getLockedAt())
                .raisedBy(vr.getRaisedBy())
                .submittedBy(vr.getSubmittedBy())
                .submittedAt(vr.getSubmittedAt())
                .issuedBy(vr.getIssuedBy())
                .issuedAt(vr.getIssuedAt())
                .approvedBy(vr.getApprovedBy())
                .approvedAt(vr.getApprovedAt())
                .triageNote(vr.getTriageNote())
                .rejectComment(vr.getRejectComment())
                .approvalRunUuid(vr.getApprovalRunUuid())
                .sourceBoqId(vr.getSourceBoqId())
                .resultBoqId(vr.getResultBoqId())
                .approvalRun(run)
                .currentContractValue(currentContract)
                .proposedContractValue(proposedContract)
                .currentMargin(currentMargin)
                .proposedMargin(proposedMargin)
                .createdAt(vr.getCreatedAt())
                .updatedAt(vr.getUpdatedAt());

        if (detail) {
            b.lines(lineRepository.findByVariationUuidOrderBySortOrderAsc(vr.getUuid()).stream()
                            .map(l -> VariationResponse.LineResponse.builder()
                                    .uuid(l.getUuid())
                                    .lineType(l.getLineType())
                                    .sourceBoqLineId(l.getSourceBoqLineId())
                                    .workItemId(l.getWorkItemId())
                                    .description(l.getDescription())
                                    .unit(l.getUnit())
                                    .quantity(l.getQuantity())
                                    .sellRate(l.getSellRate())
                                    .sellAmount(l.getSellAmount())
                                    .costRate(l.getCostRate())
                                    .costAmount(l.getCostAmount())
                                    .sortOrder(l.getSortOrder())
                                    .build())
                            .toList())
                    .links(linkRepository.findByVariationUuid(vr.getUuid()).stream()
                            .map(l -> VariationResponse.LinkResponse.builder()
                                    .uuid(l.getUuid())
                                    .linkType(l.getLinkType())
                                    .roomId(l.getRoomId())
                                    .activityUuid(l.getActivityUuid())
                                    .build())
                            .toList())
                    .attachments(attachmentRepository.findByVariationUuidOrderByCreatedAtAsc(vr.getUuid()).stream()
                            .map(a -> VariationResponse.AttachmentResponse.builder()
                                    .uuid(a.getUuid())
                                    .filePath(a.getFilePath())
                                    .downloadUrl(attachmentDownloadUrl(vr, a))
                                    .originalName(a.getOriginalName())
                                    .uploadedBy(a.getUploadedBy())
                                    .createdAt(a.getCreatedAt())
                                    .build())
                            .toList())
                    .events(eventRepository.findByVariationUuidOrderByCreatedAtAsc(vr.getUuid()).stream()
                            .map(e -> VariationResponse.EventResponse.builder()
                                    .uuid(e.getUuid())
                                    .action(e.getAction())
                                    .fromStatus(e.getFromStatus())
                                    .toStatus(e.getToStatus())
                                    .actorId(e.getActorId())
                                    .detail(e.getDetail())
                                    .previousContractValue(e.getPreviousContractValue())
                                    .newContractValue(e.getNewContractValue())
                                    .previousMargin(e.getPreviousMargin())
                                    .newMargin(e.getNewMargin())
                                    .createdAt(e.getCreatedAt())
                                    .build())
                            .toList());
        }
        return b.build();
    }

    private void appendEvent(VariationRequest vr, String action, String from, String to,
                             Long actorId, String detail) {
        VariationEvent event = new VariationEvent();
        event.setVariationUuid(vr.getUuid());
        event.setAction(action);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorId(actorId);
        event.setDetail(detail);
        eventRepository.save(event);
    }

    private Long resolveMatrixIssuer(UUID runUuid) {
        try {
            ApprovalRunResponse run = commercialApprovalService.getRunInternal(runUuid);
            if (run != null && run.getTasks() != null) {
                return run.getTasks().stream()
                        .filter(t -> t.getDecidedBy() != null)
                        .max(java.util.Comparator.comparingInt(ApprovalRunResponse.TaskResponse::getStepOrder))
                        .map(ApprovalRunResponse.TaskResponse::getDecidedBy)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Could not resolve issuer from approval run {}: {}", runUuid, e.getMessage());
        }
        return null;
    }

    private String attachmentDownloadUrl(VariationRequest vr, VariationAttachment attachment) {
        String publicUrl = fileStorageService.publicUrl(attachment.getFilePath());
        return publicUrl != null ? publicUrl
                : "/api/projects/" + vr.getProjectId() + "/variations/" + vr.getUuid()
                + "/attachments/" + attachment.getUuid();
    }

    private void notifyClient(VariationRequest vr, String category, String title, String body, String link) {
        Project project = projectService.getById(vr.getProjectId());
        if (project.getClientId() != null) {
            raiseAlert(vr, project.getClientId(), category, "INFO", title, body, link,
                    "vr-client:" + vr.getUuid() + ":" + category);
        }
    }

    private void notifyPms(VariationRequest vr, String category, String title, String body, String link) {
        for (Account a : accountRepository.findAllByCompanyUuidAndRole(vr.getCompanyId(), Role.PROJECT_MANAGER)) {
            raiseAlert(vr, a.getId(), category, "INFO", title, body, link,
                    "vr-pm:" + vr.getUuid() + ":" + a.getId());
        }
        for (Account a : accountRepository.findAllByCompanyUuidAndRole(vr.getCompanyId(), Role.ADMIN)) {
            raiseAlert(vr, a.getId(), category, "INFO", title, body, link,
                    "vr-admin:" + vr.getUuid() + ":" + a.getId());
        }
    }

    private void notifyStaff(VariationRequest vr, String category, String title, String body, String link) {
        if (vr.getSubmittedBy() != null) {
            raiseAlert(vr, vr.getSubmittedBy(), category, "INFO", title, body, link,
                    "vr-staff:" + vr.getUuid() + ":" + category);
        }
        notifyPms(vr, category, title, body, link);
    }

    private void raiseAlert(VariationRequest vr, Long accountId, String category, String severity,
                            String title, String body, String link, String dedupeKey) {
        notificationService.raise(new NotificationService.Alert(
                vr.getCompanyId(), accountId, category, severity, title, body, link,
                "VARIATION", vr.getUuid(), dedupeKey, false));
    }

    private VariationRequest requireVariation(UUID uuid, Long projectId) {
        VariationRequest vr = variationRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Variation not found"));
        if (!vr.getProjectId().equals(projectId)) {
            throw new BadRequestException("Variation does not belong to this project");
        }
        return vr;
    }

    private Project requireProject(Long projectId) {
        return projectService.getById(projectId);
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Not authenticated");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        return requireStaff(STAFF);
    }

    private AuthPrincipal requireStaff(Set<Role> roles) {
        AuthPrincipal p = requirePrincipal();
        if (!hasAnyRole(p, roles)) {
            throw new ForbiddenException("Staff access required");
        }
        return p;
    }

    private AuthPrincipal requireClient() {
        AuthPrincipal p = requirePrincipal();
        if (!isPureClient(p) && !hasAnyRole(p, EnumSet.of(Role.CLIENT))) {
            // Allow accounts that include CLIENT
            if (p.getRoles() == null || !p.getRoles().contains(Role.CLIENT)) {
                throw new ForbiddenException("Client access required");
            }
        }
        return p;
    }

    private boolean isPureClient(AuthPrincipal principal) {
        return principal.getRoles() != null
                && principal.getRoles().size() == 1
                && principal.getRoles().contains(Role.CLIENT);
    }

    private boolean hasAnyRole(AuthPrincipal principal, Set<Role> roles) {
        return principal.getRoles() != null && principal.getRoles().stream().anyMatch(roles::contains);
    }

    private void assertClientOwns(Project project, AuthPrincipal principal) {
        if (project.getClientId() == null || !project.getClientId().equals(principal.getAccountId())) {
            throw new ForbiddenException("Not your project");
        }
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) throw new ForbiddenException("Company context required");
        return companyId;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
