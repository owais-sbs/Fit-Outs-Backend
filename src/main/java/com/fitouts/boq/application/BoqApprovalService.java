package com.fitouts.boq.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.api.*;
import com.fitouts.boq.domain.*;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqApprovalAction;
import com.fitouts.shared.enums.BoqApprovalStep;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.PortalAccessHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BoqApprovalService {

    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final BoqApprovalLogRepository boqApprovalLogRepository;
    private final BoqAuthHelper boqAuthHelper;
    private final BoqService boqService;
    private final PortalAccessHelper portalAccess;
    private final BoqProjectRules boqProjectRules;

    public BoqDocumentResponse submitForApproval(UUID boqId) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        boqAuthHelper.requireSubmitRole(principal);

        BoqDocument doc = findDocument(boqId);
        boqProjectRules.assertNotObsolete(doc);
        boqProjectRules.assertNotFrozen(doc.getProject().getId());
        if (doc.getStatus() != BoqDocumentStatus.DRAFT) {
            throw new BadRequestException("Only draft BOQs can be submitted for approval");
        }
        List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(boqId);
        if (lines.isEmpty()) {
            throw new BadRequestException("BOQ must have at least one line before submission");
        }

        doc.setStatus(BoqDocumentStatus.PENDING_SENIOR_QS);
        doc.setCurrentApprovalStep(BoqApprovalStep.SENIOR_QS.name());
        doc.setSubmittedAt(LocalDateTime.now());
        doc.setSubmittedBy(principal.getAccountId());
        doc.setLastRejectionComment(null);
        boqDocumentRepository.save(doc);
        boqProjectRules.obsoleteOthers(doc.getProject().getId(), doc.getId());

        appendLog(doc, BoqApprovalStep.QS_SUBMIT, BoqApprovalAction.SUBMITTED, principal, null);
        return boqService.getById(boqId);
    }

    public BoqDocumentResponse approve(UUID boqId, String comments) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        BoqDocument doc = findDocument(boqId);
        boqProjectRules.assertNotObsolete(doc);
        assertClientOwnsIfNeeded(principal, doc);
        boqAuthHelper.requireApproverForStatus(principal, doc.getStatus());

        if (boqAuthHelper.nextStatusAfterApproval(doc.getStatus()) == BoqDocumentStatus.APPROVED) {
            Optional<BoqDocument> approved = boqProjectRules.findApproved(doc.getProject().getId());
            if (approved.isPresent() && !approved.get().getId().equals(boqId)) {
                throw new BadRequestException(
                        "This project already has an approved BOQ. Further BOQs cannot be submitted or approved.");
            }
        }

        BoqApprovalStep step = boqAuthHelper.stepForStatus(doc.getStatus());
        BoqDocumentStatus next = boqAuthHelper.nextStatusAfterApproval(doc.getStatus());

        doc.setStatus(next);
        if (next == BoqDocumentStatus.APPROVED) {
            doc.setCurrentApprovalStep(null);
            doc.setApprovedAt(LocalDateTime.now());
            doc.setApprovedBy(principal.getAccountId());
            boqProjectRules.obsoleteOthers(doc.getProject().getId(), doc.getId());
        } else {
            BoqApprovalStep nextStep = boqAuthHelper.stepForStatus(next);
            doc.setCurrentApprovalStep(nextStep != null ? nextStep.name() : null);
        }
        boqDocumentRepository.save(doc);

        appendLog(doc, step, BoqApprovalAction.APPROVED, principal, comments);
        return boqService.getById(boqId);
    }

    public BoqDocumentResponse reject(UUID boqId, String comments) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        BoqDocument doc = findDocument(boqId);
        assertClientOwnsIfNeeded(principal, doc);
        boqAuthHelper.requireApproverForStatus(principal, doc.getStatus());

        if (!StringUtils.hasText(comments)) {
            throw new BadRequestException("Rejection comment is required");
        }

        BoqApprovalStep step = boqAuthHelper.stepForStatus(doc.getStatus());
        doc.setStatus(BoqDocumentStatus.DRAFT);
        doc.setCurrentApprovalStep(null);
        doc.setLastRejectionComment(comments.trim());
        boqDocumentRepository.save(doc);

        appendLog(doc, step, BoqApprovalAction.REJECTED, principal, comments.trim());
        return boqService.getById(boqId);
    }

    public BoqDocumentResponse createRevision(UUID boqId, String revisionLabel) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        boqAuthHelper.requireSubmitRole(principal);
        BoqDocument doc = findDocument(boqId);
        boqProjectRules.assertNotFrozen(doc.getProject().getId());
        throw new BadRequestException(
                "Separate revisions are not used. Save a new survey BOQ to replace the pending version.");
    }

    @Transactional(readOnly = true)
    public BoqApprovalHistoryResponse getApprovalHistory(UUID boqId) {
        BoqDocument doc = findDocument(boqId);
        Long projectId = doc.getProject().getId();
        List<BoqDocument> versions = new ArrayList<>(boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
        versions.sort(Comparator.comparing(BoqDocument::getCreatedAt));

        List<UUID> ids = versions.stream().map(BoqDocument::getId).collect(Collectors.toList());
        List<BoqApprovalLog> log = ids.isEmpty()
                ? List.of()
                : boqApprovalLogRepository.findByBoqIdInOrderByCreatedAtAsc(ids);

        return BoqApprovalHistoryResponse.builder()
                .boqId(boqId)
                .log(log.stream().map(this::mapLog).collect(Collectors.toList()))
                .versions(versions.stream().map(this::mapVersion).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<BoqInboxItemResponse> listPendingForCurrentUser(String roleParam) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        UUID companyId = CompanyContext.get();

        List<BoqDocumentStatus> statuses = boqAuthHelper.inboxStatusesForPrincipal(principal, roleParam);
        if (statuses.isEmpty()) {
            return List.of();
        }

        return boqDocumentRepository.findByCompanyIdAndStatusInOrderBySubmittedAtDesc(companyId, statuses)
                .stream()
                .filter(doc -> {
                    if (!portalAccess.isPureClient(principal)) {
                        return true;
                    }
                    return doc.getProject() != null
                            && Objects.equals(doc.getProject().getClientId(), principal.getAccountId());
                })
                .map(this::mapInboxItem)
                .collect(Collectors.toList());
    }

    private void appendLog(BoqDocument doc, BoqApprovalStep step, BoqApprovalAction action,
            AuthPrincipal principal, String comments) {
        String roleName = principal.getRoles().isEmpty() ? null : principal.getRoles().iterator().next().name();
        boqApprovalLogRepository.save(BoqApprovalLog.builder()
                .boq(doc)
                .step(step)
                .action(action)
                .actorId(principal.getAccountId())
                .actorRole(roleName)
                .actorName(principal.getFullName())
                .comments(comments)
                .build());
    }

    private BoqDocument findDocument(UUID id) {
        UUID companyId = CompanyContext.get();
        BoqDocument doc = boqDocumentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("BOQ not found"));
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        assertClientOwnsIfNeeded(principal, doc);
        return doc;
    }

    private void assertClientOwnsIfNeeded(AuthPrincipal principal, BoqDocument doc) {
        if (!portalAccess.isPureClient(principal)) {
            return;
        }
        if (doc.getProject() == null
                || !Objects.equals(doc.getProject().getClientId(), principal.getAccountId())) {
            throw new ForbiddenException("Not your BOQ");
        }
        // Clients may only view/act on pending-client or later statuses
        if (doc.getStatus() == BoqDocumentStatus.DRAFT
                || doc.getStatus() == BoqDocumentStatus.PENDING_SENIOR_QS
                || doc.getStatus() == BoqDocumentStatus.PENDING_PM
                || doc.getStatus() == BoqDocumentStatus.PENDING_DIRECTOR
                || doc.getStatus() == BoqDocumentStatus.OBSOLETE) {
            throw new ForbiddenException("BOQ is not ready for client review");
        }
    }

    private BoqApprovalLogResponse mapLog(BoqApprovalLog log) {
        return BoqApprovalLogResponse.builder()
                .id(log.getId())
                .step(log.getStep())
                .action(log.getAction())
                .actorId(log.getActorId())
                .actorRole(log.getActorRole())
                .actorName(log.getActorName())
                .comments(log.getComments())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private BoqVersionSummaryResponse mapVersion(BoqDocument doc) {
        return BoqVersionSummaryResponse.builder()
                .id(doc.getId())
                .version(doc.getVersion())
                .revisionLabel(doc.getRevisionLabel())
                .status(doc.getStatus())
                .grandTotal(doc.getGrandTotal())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private BoqInboxItemResponse mapInboxItem(BoqDocument doc) {
        return BoqInboxItemResponse.builder()
                .id(doc.getId())
                .projectId(doc.getProject().getId())
                .projectName(doc.getProject().getName())
                .version(doc.getVersion())
                .status(doc.getStatus())
                .currentApprovalStep(doc.getCurrentApprovalStep())
                .grandTotal(doc.getGrandTotal())
                .submittedAt(doc.getSubmittedAt())
                .build();
    }
}
