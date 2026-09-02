package com.fitouts.checklist.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.domain.SiteVisitEstimateLine;
import com.fitouts.checklist.dto.SiteVisitEstimateLineRequest;
import com.fitouts.checklist.dto.SiteVisitEstimateLineResponse;
import com.fitouts.checklist.dto.SiteVisitEstimateRequest;
import com.fitouts.checklist.dto.SiteVisitEstimateResponse;

@Component
public class SiteVisitEstimateMapper {

    public SiteVisitEstimateResponse toResponse(SiteVisitEstimate estimate) {
        return SiteVisitEstimateResponse.builder()
                .uuid(estimate.getUuid())
                .siteVisitUuid(estimate.getSiteVisit() != null ? estimate.getSiteVisit().getUuid() : null)
                .quoteNo(estimate.getQuoteNo())
                .validUntil(estimate.getValidUntil())
                .revision(estimate.getRevision())
                .clientName(estimate.getClientName())
                .clientAddress(estimate.getClientAddress())
                .projectLabel(estimate.getProjectLabel())
                .locationLabel(estimate.getLocationLabel())
                .subject(estimate.getSubject())
                .preparedBy(estimate.getPreparedBy())
                .currency(estimate.getCurrency())
                .notes(estimate.getNotes())
                .subtotal(estimate.getSubtotal())
                .status(estimate.getStatus())
                .excludedScopeRefs(estimate.getExcludedScopeRefs() != null
                        ? new ArrayList<>(estimate.getExcludedScopeRefs())
                        : new ArrayList<>())
                .lines(estimate.getLines() == null
                        ? new ArrayList<>()
                        : estimate.getLines().stream().map(this::toLineResponse).toList())
                .createdAt(estimate.getCreatedAt())
                .updatedAt(estimate.getUpdatedAt())
                .build();
    }

    public SiteVisitEstimateLineResponse toLineResponse(SiteVisitEstimateLine line) {
        return SiteVisitEstimateLineResponse.builder()
                .uuid(line.getUuid())
                .workItemId(line.getWorkItemId())
                .roomTypeId(line.getRoomTypeId())
                .floorName(line.getFloorName())
                .roomName(line.getRoomName())
                .category(line.getCategory())
                .description(line.getDescription())
                .qty(line.getQty())
                .unit(line.getUnit())
                .rate(line.getRate())
                .amount(line.getAmount())
                .displayOrder(line.getDisplayOrder())
                .lineSource(line.getLineSource())
                .scopeRef(line.getScopeRef())
                .build();
    }

    public void applyRequest(SiteVisitEstimate estimate, SiteVisitEstimateRequest request) {
        if (request.getQuoteNo() != null) {
            estimate.setQuoteNo(trimNullable(request.getQuoteNo()));
        }
        if (request.getValidUntil() != null) {
            estimate.setValidUntil(request.getValidUntil());
        }
        if (request.getRevision() != null && !request.getRevision().isBlank()) {
            estimate.setRevision(request.getRevision().trim());
        }
        if (request.getClientName() != null) {
            estimate.setClientName(trimNullable(request.getClientName()));
        }
        if (request.getClientAddress() != null) {
            estimate.setClientAddress(trimNullable(request.getClientAddress()));
        }
        if (request.getProjectLabel() != null) {
            estimate.setProjectLabel(trimNullable(request.getProjectLabel()));
        }
        if (request.getLocationLabel() != null) {
            estimate.setLocationLabel(trimNullable(request.getLocationLabel()));
        }
        if (request.getSubject() != null) {
            estimate.setSubject(trimNullable(request.getSubject()));
        }
        if (request.getPreparedBy() != null) {
            estimate.setPreparedBy(trimNullable(request.getPreparedBy()));
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            estimate.setCurrency(request.getCurrency().trim());
        }
        if (request.getNotes() != null) {
            estimate.setNotes(trimNullable(request.getNotes()));
        }
        if (request.getExcludedScopeRefs() != null) {
            estimate.setExcludedScopeRefs(new ArrayList<>(request.getExcludedScopeRefs()));
        }

        List<SiteVisitEstimateLine> lines = new ArrayList<>();
        List<SiteVisitEstimateLineRequest> lineRequests =
                request.getLines() != null ? request.getLines() : List.of();
        int order = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SiteVisitEstimateLineRequest lineRequest : lineRequests) {
            if (lineRequest == null) {
                continue;
            }
            String description = trimNullable(lineRequest.getDescription());
            if (description == null) {
                continue;
            }
            SiteVisitEstimateLine line = new SiteVisitEstimateLine();
            line.setWorkItemId(lineRequest.getWorkItemId());
            line.setRoomTypeId(lineRequest.getRoomTypeId());
            line.setFloorName(trimNullable(lineRequest.getFloorName()));
            line.setRoomName(trimNullable(lineRequest.getRoomName()));
            line.setCategory(trimNullable(lineRequest.getCategory()));
            line.setDescription(description);
            BigDecimal qty = normalizeNumber(lineRequest.getQty(), BigDecimal.ONE);
            BigDecimal rate = normalizeNumber(lineRequest.getRate(), BigDecimal.ZERO);
            BigDecimal amount = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            line.setQty(qty);
            line.setUnit(lineRequest.getUnit() == null || lineRequest.getUnit().isBlank()
                    ? "LS"
                    : lineRequest.getUnit().trim());
            line.setRate(rate);
            line.setAmount(amount);
            line.setDisplayOrder(lineRequest.getDisplayOrder() != null ? lineRequest.getDisplayOrder() : order);
            line.setLineSource(trimNullable(lineRequest.getLineSource()));
            line.setScopeRef(trimNullable(lineRequest.getScopeRef()));
            lines.add(line);
            subtotal = subtotal.add(amount);
            order++;
        }
        estimate.clearAndAddLines(lines);
        estimate.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal normalizeNumber(BigDecimal value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
