package com.fitouts.boq.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

    public BoqDocumentResponse submitForApproval(UUID boqId) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        boqAuthHelper.requireSubmitRole(principal);

        BoqDocument doc = findDocument(boqId);
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

        appendLog(doc, BoqApprovalStep.QS_SUBMIT, BoqApprovalAction.SUBMITTED, principal, null);
        return boqService.getById(boqId);
    }

    public BoqDocumentResponse approve(UUID boqId, String comments) {
        AuthPrincipal principal = boqAuthHelper.requirePrincipal();
        BoqDocument doc = findDocument(boqId);
        assertClientOwnsIfNeeded(principal, doc);
        boqAuthHelper.requireApproverForStatus(principal, doc.getStatus());

        BoqApprovalStep step = boqAuthHelper.stepForStatus(doc.getStatus());
        BoqDocumentStatus next = boqAuthHelper.nextStatusAfterApproval(doc.getStatus());

        doc.setStatus(next);
        if (next == BoqDocumentStatus.APPROVED) {
            doc.setCurrentApprovalStep(null);
            doc.setApprovedAt(LocalDateTime.now());
            doc.setApprovedBy(principal.getAccountId());
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

        BoqDocument source = findDocument(boqId);
        if (source.getStatus() != BoqDocumentStatus.APPROVED && source.getStatus() != BoqDocumentStatus.FINAL) {
            throw new BadRequestException("Only approved BOQs can be revised");
        }

        UUID rootId = source.getParentBoq() != null ? source.getParentBoq().getId() : source.getId();
        List<BoqDocument> chain = new ArrayList<>(boqDocumentRepository.findByParentBoqIdOrderByCreatedAtAsc(rootId));
        chain.add(source);
        chain.sort(Comparator.comparing(BoqDocument::getCreatedAt));

        String nextVersion = incrementVersion(chain.get(chain.size() - 1).getVersion());

        BoqDocument revision = BoqDocument.builder()
                .project(source.getProject())
                .companyId(source.getCompanyId())
                .qtoSession(source.getQtoSession())
                .parentBoq(boqDocumentRepository.getReferenceById(rootId))
                .version(nextVersion)
                .revisionLabel(StringUtils.hasText(revisionLabel) ? revisionLabel.trim() : "Revision")
                .status(BoqDocumentStatus.DRAFT)
                .notes(source.getNotes())
                .subtotal(source.getSubtotal())
                .vatAmount(source.getVatAmount())
                .grandTotal(source.getGrandTotal())
                .build();
        BoqDocument saved = boqDocumentRepository.save(revision);

        List<BoqLine> sourceLines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(source.getId());
        for (BoqLine line : sourceLines) {
            boqLineRepository.save(BoqLine.builder()
                    .boq(saved)
                    .categoryCode(line.getCategoryCode())
                    .categoryName(line.getCategoryName())
                    .description(line.getDescription())
                    .unit(line.getUnit())
                    .quantity(line.getQuantity())
                    .rate(line.getRate())
                    .amount(line.getAmount())
                    .qtoLine(line.getQtoLine())
                    .floorLabel(line.getFloorLabel())
                    .roomLabel(line.getRoomLabel())
                    .sortOrder(line.getSortOrder())
                    .source(line.getSource())
                    .build());
        }

        appendLog(saved, BoqApprovalStep.QS_SUBMIT, BoqApprovalAction.REVISION_CREATED, principal,
                "Created from v" + source.getVersion());
        return boqService.getById(saved.getId());
    }

    @Transactional(readOnly = true)
    public BoqApprovalHistoryResponse getApprovalHistory(UUID boqId) {
        BoqDocument doc = findDocument(boqId);
        UUID rootId = doc.getParentBoq() != null ? doc.getParentBoq().getId() : doc.getId();

        List<BoqDocument> versions = new ArrayList<>();
        boqDocumentRepository.findById(rootId).ifPresent(versions::add);
        versions.addAll(boqDocumentRepository.findByParentBoqIdOrderByCreatedAtAsc(rootId));
        if (!versions.stream().anyMatch(v -> v.getId().equals(doc.getId()))) {
            versions.add(doc);
        }
        versions.sort(Comparator.comparing(BoqDocument::getCreatedAt));

        List<BoqApprovalLog> log = boqApprovalLogRepository.findByBoqIdOrderByCreatedAtAsc(boqId);

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
                || doc.getStatus() == BoqDocumentStatus.PENDING_DIRECTOR) {
            throw new ForbiddenException("BOQ is not ready for client review");
        }
    }

    private String incrementVersion(String current) {
        if (!StringUtils.hasText(current)) {
            return "1.1";
        }
        String[] parts = current.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            minor += 1;
            return major + "." + minor;
        } catch (NumberFormatException e) {
            return current + ".1";
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
