package com.fitouts.boq.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.boq.api.*;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.qto.application.QtoService;
import com.fitouts.qto.domain.QtoLine;
import com.fitouts.qto.domain.QtoLineRepository;
import com.fitouts.qto.domain.QtoSession;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BoqService {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.05");

    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final QtoService qtoService;
    private final QtoLineRepository qtoLineRepository;
    private final ProjectService projectService;

    public BoqDocumentResponse generateFromQto(UUID sessionId) {
        QtoSession session = qtoService.findSession(sessionId);
        List<QtoLine> qtoLines = qtoLineRepository.findBySessionIdOrderBySortOrderAsc(sessionId);

        BoqDocument doc = BoqDocument.builder()
                .project(session.getProject())
                .companyId(session.getCompanyId())
                .qtoSession(session)
                .version("1.0")
                .status(BoqDocumentStatus.DRAFT)
                .build();
        BoqDocument saved = boqDocumentRepository.save(doc);

        List<BoqLine> lines = new ArrayList<>();
        int order = 0;
        for (QtoLine ql : qtoLines) {
            String code = BoqJctMapper.categoryCode(ql.getLineType());
            String name = BoqJctMapper.categoryName(code);
            BigDecimal amount = ql.getAmount() != null ? ql.getAmount() : BigDecimal.ZERO;
            lines.add(boqLineRepository.save(BoqLine.builder()
                    .boq(saved)
                    .categoryCode(code)
                    .categoryName(name)
                    .description(ql.getLabel())
                    .unit(ql.getUnit())
                    .quantity(ql.getQuantity())
                    .rate(ql.getRate() != null ? ql.getRate() : BigDecimal.ZERO)
                    .amount(amount)
                    .qtoLine(ql)
                    .sortOrder(order++)
                    .source("QTO")
                    .build()));
        }
        recalcTotals(saved, lines);
        return mapDocument(saved, lines);
    }

    public BoqDocumentResponse saveFromSurvey(BoqSurveySaveRequest request) {
        UUID companyId = CompanyContext.get();
        Project project = projectService.getById(request.getProjectId());
        BoqDocument doc = BoqDocument.builder()
                .project(project)
                .companyId(companyId)
                .version(request.getVersion() != null ? request.getVersion() : "1.0")
                .status(BoqDocumentStatus.DRAFT)
                .notes(request.getNotes())
                .build();
        BoqDocument saved = boqDocumentRepository.save(doc);
        List<BoqLine> lines = saveLines(saved, request.getLines());
        recalcTotals(saved, lines);
        return mapDocument(saved, lines);
    }

    public BoqDocumentResponse update(UUID id, BoqUpdateRequest request) {
        BoqDocument doc = findDocument(id);
        assertEditable(doc);
        if (request.getNotes() != null) {
            doc.setNotes(request.getNotes());
        }
        boqLineRepository.deleteByBoqId(id);
        List<BoqLine> lines = saveLines(doc, request.getLines());
        recalcTotals(doc, lines);
        return mapDocument(boqDocumentRepository.save(doc), lines);
    }

    public BoqDocumentResponse finalizeBoq(UUID id) {
        BoqDocument doc = findDocument(id);
        doc.setStatus(BoqDocumentStatus.APPROVED);
        doc.setApprovedAt(java.time.LocalDateTime.now());
        List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(id);
        return mapDocument(boqDocumentRepository.save(doc), lines);
    }

    private void assertEditable(BoqDocument doc) {
        if (doc.getStatus() != BoqDocumentStatus.DRAFT) {
            throw new BadRequestException("BOQ can only be edited while in DRAFT status");
        }
    }

    @Transactional(readOnly = true)
    public BoqDocumentResponse getById(UUID id) {
        BoqDocument doc = findDocument(id);
        List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(id);
        return mapDocument(doc, lines);
    }

    @Transactional(readOnly = true)
    public List<BoqDocumentResponse> listByProject(Long projectId) {
        return boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(d -> mapDocument(d, boqLineRepository.findByBoqIdOrderBySortOrderAsc(d.getId())))
                .collect(Collectors.toList());
    }

    private List<BoqLine> saveLines(BoqDocument doc, List<BoqLineRequest> requests) {
        List<BoqLine> lines = new ArrayList<>();
        if (requests == null) return lines;
        int order = 0;
        for (BoqLineRequest req : requests) {
            BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ZERO;
            BigDecimal rate = req.getRate() != null ? req.getRate() : BigDecimal.ZERO;
            BigDecimal amount = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            String code = req.getCategoryCode() != null ? req.getCategoryCode() : "OTHER";
            String name = req.getCategoryName() != null ? req.getCategoryName() : BoqJctMapper.categoryName(code);
            lines.add(boqLineRepository.save(BoqLine.builder()
                    .boq(doc)
                    .categoryCode(code)
                    .categoryName(name)
                    .description(req.getDescription())
                    .unit(req.getUnit())
                    .quantity(qty)
                    .rate(rate)
                    .amount(amount)
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : order++)
                    .floorLabel(req.getFloorLabel())
                    .roomLabel(req.getRoomLabel())
                    .source(req.getSource() != null ? req.getSource() : "SURVEY")
                    .build()));
        }
        return lines;
    }

    private void recalcTotals(BoqDocument doc, List<BoqLine> lines) {
        BigDecimal subtotal = lines.stream()
                .map(l -> l.getAmount() != null ? l.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        doc.setSubtotal(subtotal);
        doc.setVatAmount(vat);
        doc.setGrandTotal(subtotal.add(vat));
        boqDocumentRepository.save(doc);
    }

    private BoqDocument findDocument(UUID id) {
        UUID companyId = CompanyContext.get();
        return boqDocumentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("BOQ not found"));
    }

    private BoqDocumentResponse mapDocument(BoqDocument doc, List<BoqLine> lines) {
        return BoqDocumentResponse.builder()
                .id(doc.getId())
                .projectId(doc.getProject().getId())
                .projectName(doc.getProject().getName())
                .qtoSessionId(doc.getQtoSession() != null ? doc.getQtoSession().getId() : null)
                .parentBoqId(doc.getParentBoq() != null ? doc.getParentBoq().getId() : null)
                .version(doc.getVersion())
                .revisionLabel(doc.getRevisionLabel())
                .status(doc.getStatus())
                .currentApprovalStep(doc.getCurrentApprovalStep())
                .subtotal(doc.getSubtotal())
                .vatAmount(doc.getVatAmount())
                .grandTotal(doc.getGrandTotal())
                .notes(doc.getNotes())
                .lastRejectionComment(doc.getLastRejectionComment())
                .lines(lines.stream().map(this::mapLine).collect(Collectors.toList()))
                .submittedAt(doc.getSubmittedAt())
                .approvedAt(doc.getApprovedAt())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private BoqLineResponse mapLine(BoqLine line) {
        return BoqLineResponse.builder()
                .id(line.getId())
                .categoryCode(line.getCategoryCode())
                .categoryName(line.getCategoryName())
                .description(line.getDescription())
                .unit(line.getUnit())
                .quantity(line.getQuantity())
                .rate(line.getRate())
                .amount(line.getAmount())
                .qtoLineId(line.getQtoLine() != null ? line.getQtoLine().getId() : null)
                .floorLabel(line.getFloorLabel())
                .roomLabel(line.getRoomLabel())
                .sortOrder(line.getSortOrder())
                .source(line.getSource())
                .build();
    }
}
