package com.fitouts.qto.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.drawing.application.ProjectDrawingService;
import com.fitouts.drawing.domain.ProjectDrawing;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.qto.api.*;
import com.fitouts.qto.domain.QtoLine;
import com.fitouts.qto.domain.QtoLineRepository;
import com.fitouts.qto.domain.QtoSession;
import com.fitouts.qto.domain.QtoSessionRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.QtoLineSource;
import com.fitouts.shared.enums.QtoLineType;
import com.fitouts.shared.enums.QtoSessionStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class QtoService {

    private final QtoSessionRepository sessionRepository;
    private final QtoLineRepository lineRepository;
    private final ProjectService projectService;
    private final ProjectDrawingService drawingService;
    private final WorkItemRepository workItemRepository;

    public QtoSessionResponse createSession(QtoSessionCreateRequest request) {
        UUID companyId = CompanyContext.get();
        Project project = projectService.getById(request.getProjectId());
        ProjectDrawing drawing = null;
        if (request.getDrawingId() != null) {
            drawing = drawingService.find(request.getDrawingId());
        }
        QtoSession session = QtoSession.builder()
                .project(project)
                .companyId(companyId)
                .drawing(drawing)
                .notes(request.getNotes())
                .status(QtoSessionStatus.DRAFT)
                .build();
        return mapSession(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public QtoSessionResponse getSession(UUID id) {
        return mapSession(findSession(id));
    }

    @Transactional(readOnly = true)
    public List<QtoSessionResponse> listByProject(Long projectId) {
        return sessionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(this::mapSession).collect(Collectors.toList());
    }

    public QtoSessionResponse updateScale(UUID id, QtoScaleRequest request) {
        QtoSession session = findSession(id);
        session.setScaleRatio(request.getScaleRatio());
        if (request.getScaleUnit() != null) {
            session.setScaleUnit(request.getScaleUnit());
        }
        return mapSession(sessionRepository.save(session));
    }

    public QtoSessionResponse replaceLines(UUID id, QtoLinesUpdateRequest request) {
        QtoSession session = findSession(id);
        if (session.getStatus() == QtoSessionStatus.APPROVED) {
            throw new BadRequestException("Cannot edit approved QTO session");
        }
        lineRepository.deleteBySessionId(id);
        List<QtoLine> saved = new ArrayList<>();
        if (request.getLines() != null) {
            int order = 0;
            for (QtoLineRequest lineReq : request.getLines()) {
                saved.add(lineRepository.save(buildLine(session, lineReq, order++)));
            }
        }
        return mapSessionWithLines(session, saved);
    }

    public QtoSessionResponse approve(UUID id) {
        QtoSession session = findSession(id);
        session.setStatus(QtoSessionStatus.APPROVED);
        return mapSession(sessionRepository.save(session));
    }

    private QtoLine buildLine(QtoSession session, QtoLineRequest req, int order) {
        WorkItem workItem = null;
        BigDecimal rate = req.getRate();
        if (req.getWorkItemId() != null) {
            workItem = workItemRepository.findByIdAndDeletedFalse(req.getWorkItemId())
                    .orElseThrow(() -> new NotFoundException("Work item not found"));
            if (rate == null) {
                rate = workItem.getDefaultRate();
            }
        }
        BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ZERO;
        BigDecimal amount = rate != null
                ? qty.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        QtoLineType type = req.getLineType() != null ? req.getLineType() : QtoLineType.CUSTOM;
        String unit = req.getUnit() != null ? req.getUnit() : defaultUnit(type);
        String label = req.getLabel() != null ? req.getLabel() : type.name().replace('_', ' ');

        return QtoLine.builder()
                .session(session)
                .lineType(type)
                .label(label)
                .quantity(qty)
                .unit(unit)
                .workItem(workItem)
                .rate(rate)
                .amount(amount)
                .geometryJson(req.getGeometryJson())
                .source(req.getSource() != null ? req.getSource() : QtoLineSource.MANUAL)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : order)
                .build();
    }

    private String defaultUnit(QtoLineType type) {
        return switch (type) {
            case DOOR_COUNT, WINDOW_COUNT, PLUMBING_FIXTURE, LIGHTING_FIXTURE -> "PCS";
            case SKIRTING_LENGTH -> "RMT";
            default -> "SQM";
        };
    }

    public QtoSession findSession(UUID id) {
        UUID companyId = CompanyContext.get();
        return sessionRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("QTO session not found"));
    }

    private QtoSessionResponse mapSession(QtoSession session) {
        List<QtoLine> lines = lineRepository.findBySessionIdOrderBySortOrderAsc(session.getId());
        return mapSessionWithLines(session, lines);
    }

    private QtoSessionResponse mapSessionWithLines(QtoSession session, List<QtoLine> lines) {
        return QtoSessionResponse.builder()
                .id(session.getId())
                .projectId(session.getProject().getId())
                .drawingId(session.getDrawing() != null ? session.getDrawing().getId() : null)
                .drawingName(session.getDrawing() != null ? session.getDrawing().getFileName() : null)
                .status(session.getStatus())
                .scaleRatio(session.getScaleRatio())
                .scaleUnit(session.getScaleUnit())
                .notes(session.getNotes())
                .lines(lines.stream().map(this::mapLine).collect(Collectors.toList()))
                .build();
    }

    private QtoLineResponse mapLine(QtoLine line) {
        return QtoLineResponse.builder()
                .id(line.getId())
                .lineType(line.getLineType())
                .label(line.getLabel())
                .quantity(line.getQuantity())
                .unit(line.getUnit())
                .workItemId(line.getWorkItem() != null ? line.getWorkItem().getId() : null)
                .workItemName(line.getWorkItem() != null ? line.getWorkItem().getWorkItemName() : null)
                .rate(line.getRate())
                .amount(line.getAmount())
                .geometryJson(line.getGeometryJson())
                .source(line.getSource())
                .editable(line.getEditable())
                .sortOrder(line.getSortOrder())
                .build();
    }
}
