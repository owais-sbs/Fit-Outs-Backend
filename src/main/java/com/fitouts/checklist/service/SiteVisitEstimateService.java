package com.fitouts.checklist.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.appendix.application.SiteVisitEstimateAppendixService;
import com.fitouts.appendix.dto.AppendixMasterResponse;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.domain.SiteVisitEstimateStatus;
import com.fitouts.checklist.domain.SiteVisitLocationDetails;
import com.fitouts.checklist.dto.SiteVisitEstimateRequest;
import com.fitouts.checklist.dto.SiteVisitEstimateResponse;
import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.checklist.repository.SiteVisitEstimateRepository;
import com.fitouts.checklist.repository.SiteVisitReportRepository;
import com.fitouts.lead.domain.Lead;
import com.fitouts.lead.domain.LeadRepository;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.security.PortalAccessHelper;
import com.fitouts.auth.security.AuthPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitEstimateService {

    private final SiteVisitEstimateRepository estimateRepository;
    private final SiteVisitReportRepository reportRepository;
    private final SiteVisitService siteVisitService;
    private final SiteVisitEstimateMapper mapper;
    private final LeadRepository leadRepository;
    private final SiteVisitEstimateAppendixService appendixService;
    private final PortalAccessHelper portalAccess;

    private SiteVisitEstimateResponse enrich(SiteVisitEstimate estimate) {
        SiteVisitEstimateResponse response = mapper.toResponse(estimate);
        List<UUID> ids = appendixService.getSelectedIds(estimate.getUuid());
        List<AppendixMasterResponse> appendices = appendixService.getSelectedAppendices(estimate.getUuid());
        response.setSelectedAppendixIds(ids);
        response.setSelectedAppendices(appendices);
        return response;
    }

    @Transactional
    public SiteVisitEstimateResponse getOrCreate(UUID siteVisitUuid) {
        SiteVisit visit = siteVisitService.getSiteVisit(siteVisitUuid);
        AuthPrincipal principal = portalAccess.requirePrincipal();
        if (portalAccess.isPureClient(principal)) {
            SiteVisitEstimate estimate = estimateRepository.findBySiteVisitUuid(siteVisitUuid)
                    .orElseThrow(() -> new NotFoundException("Estimate not found"));
            if (estimate.getStatus() != SiteVisitEstimateStatus.ISSUED) {
                throw new ForbiddenException("Estimate is not available yet");
            }
            return enrich(estimate);
        }
        return enrich(estimateRepository.findBySiteVisitUuid(siteVisitUuid)
                .orElseGet(() -> createDraft(visit)));
    }

    @Transactional(readOnly = true)
    public List<SiteVisitEstimateResponse> listIssuedForCurrentClient() {
        AuthPrincipal principal = portalAccess.requirePrincipal();
        if (!portalAccess.isPureClient(principal)) {
            throw new ForbiddenException("Client access only");
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return estimateRepository
                .findIssuedForClientEmail(companyId, principal.getEmail(), SiteVisitEstimateStatus.ISSUED)
                .stream()
                .map(this::enrich)
                .toList();
    }

    @Transactional
    public SiteVisitEstimateResponse upsert(UUID siteVisitUuid, SiteVisitEstimateRequest request) {
        portalAccess.requireStaff();
        SiteVisit visit = siteVisitService.getSiteVisit(siteVisitUuid);
        SiteVisitEstimate estimate = estimateRepository.findBySiteVisitUuid(siteVisitUuid)
                .orElseGet(() -> createDraft(visit));

        if (estimate.getStatus() == SiteVisitEstimateStatus.ISSUED) {
            throw new ConflictException("Issued estimates cannot be edited. Create a new revision manually if needed.");
        }

        mapper.applyRequest(estimate, request);
        SiteVisitEstimate saved = estimateRepository.save(estimate);
        if (request.getSelectedAppendixIds() != null) {
            appendixService.syncSelections(saved, request.getSelectedAppendixIds());
        }
        return enrich(saved);
    }

    @Transactional
    public SiteVisitEstimateResponse issue(UUID siteVisitUuid) {
        portalAccess.requireStaff();
        SiteVisit visit = siteVisitService.getSiteVisit(siteVisitUuid);
        if (!reportRepository.existsBySiteVisitUuid(siteVisitUuid)) {
            throw new BadRequestException("Submit the site visit checklist report before issuing an estimate");
        }

        SiteVisitEstimate estimate = estimateRepository.findBySiteVisitUuid(siteVisitUuid)
                .orElseGet(() -> createDraft(visit));

        if (estimate.getLines() == null || estimate.getLines().isEmpty()) {
            throw new BadRequestException("Add at least one draft BoQ line before issuing");
        }
        if (estimate.getStatus() == SiteVisitEstimateStatus.ISSUED) {
            return enrich(estimate);
        }

        if (estimate.getQuoteNo() == null || estimate.getQuoteNo().isBlank()) {
            estimate.setQuoteNo(generateQuoteNo());
        }
        if (estimate.getValidUntil() == null) {
            estimate.setValidUntil(LocalDate.now().plusDays(30));
        }
        estimate.setStatus(SiteVisitEstimateStatus.ISSUED);
        return enrich(estimateRepository.save(estimate));
    }

    private SiteVisitEstimate createDraft(SiteVisit visit) {
        SiteVisitEstimate estimate = new SiteVisitEstimate();
        estimate.setSiteVisit(visit);
        estimate.setCompany(visit.getCompany());
        estimate.setStatus(SiteVisitEstimateStatus.DRAFT);
        estimate.setRevision("R0");
        estimate.setCurrency("AED");
        estimate.setQuoteNo(generateQuoteNo());
        estimate.setValidUntil(LocalDate.now().plusDays(30));
        estimate.setSubtotal(BigDecimal.ZERO);

        Lead lead = leadRepository.findById(visit.getLeadId()).orElse(null);
        if (lead != null) {
            estimate.setClientName(lead.getClientName());
            if (lead.getProjectType() != null && !lead.getProjectType().isBlank()) {
                estimate.setProjectLabel(lead.getProjectType().trim());
            }
            estimate.setSubject("TURNKEY RENOVATION, DUBAI, UAE");
        }

        SiteVisitLocationDetails details = visit.getLocationDetails();
        if (details != null) {
            String address = joinAddress(details);
            if (address != null) {
                estimate.setClientAddress(address);
            }
            if ((estimate.getLocationLabel() == null || estimate.getLocationLabel().isBlank())
                    && details.getArea() != null) {
                estimate.setLocationLabel(details.getArea());
            }
        }

        if (estimate.getLocationLabel() != null && !estimate.getLocationLabel().isBlank()) {
            estimate.setSubject(
                    "TURNKEY RENOVATION FOR VILLA AT "
                            + estimate.getLocationLabel().trim().toUpperCase()
                            + ", DUBAI, UAE");
        } else if (estimate.getSubject() == null || estimate.getSubject().isBlank()) {
            estimate.setSubject("TURNKEY RENOVATION, DUBAI, UAE");
        }

        // Draft starts empty — surveyor builds BoQ from work items (checklist is reference only).
        return estimateRepository.save(estimate);
    }

    private String generateQuoteNo() {
        int year = Year.now().getValue();
        long suffix = System.currentTimeMillis() % 1000;
        return String.format("QTN-JCT-%d-%03d", year, suffix);
    }

    private String joinAddress(SiteVisitLocationDetails details) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, details.getAddressLine1());
        appendPart(sb, details.getAddressLine2());
        appendPart(sb, details.getBuildingName());
        appendPart(sb, details.getArea());
        appendPart(sb, details.getCity());
        appendPart(sb, details.getState());
        appendPart(sb, details.getCountry());
        return sb.length() == 0 ? null : sb.toString();
    }

    private void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }
}
