package com.fitouts.checklist.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fitouts.checklist.domain.ChecklistTemplateItem;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitReport;
import com.fitouts.checklist.domain.SiteVisitReportItem;
import com.fitouts.checklist.dto.SiteVisitReportItemRequest;
import com.fitouts.checklist.dto.SiteVisitReportItemResponse;
import com.fitouts.checklist.dto.SiteVisitReportRequest;
import com.fitouts.checklist.dto.SiteVisitReportResponse;

@Component
public class SiteVisitReportMapper {

    public SiteVisitReport toEntity(SiteVisitReportRequest request, SiteVisit siteVisit) {
        SiteVisitReport report = new SiteVisitReport();
        report.setSiteVisit(siteVisit);
        report.setOutcome(request.getOutcome().trim());
        report.setNotes(trimNullable(request.getNotes()));
        report.setSubmittedBy(request.getSubmittedBy());
        return report;
    }

    public SiteVisitReportItem toItemEntity(
            SiteVisitReportItemRequest request,
            ChecklistTemplateItem templateItem,
            List<String> photoUrls) {

        SiteVisitReportItem item = new SiteVisitReportItem();
        item.setTemplateItem(templateItem);
        item.setResponse(trimNullable(request.getResponse()));
        item.setRemarks(trimNullable(request.getRemarks()));
        item.setPhotoUrls(photoUrls);
        return item;
    }

    public SiteVisitReportResponse toResponse(SiteVisitReport report) {
        SiteVisitReportResponse response = SiteVisitReportResponse.builder()
                .outcome(report.getOutcome())
                .notes(report.getNotes())
                .submittedBy(report.getSubmittedBy())
                .submittedAt(report.getSubmittedAt())
                .items(report.getItems().stream()
                        .map(this::toItemResponse)
                        .toList())
                .build();
        response.setUuid(report.getUuid());
        response.setSiteVisitUuid(report.getSiteVisit().getUuid());
        return response;
    }

    private SiteVisitReportItemResponse toItemResponse(SiteVisitReportItem item) {
        SiteVisitReportItemResponse response = SiteVisitReportItemResponse.builder()
                .response(item.getResponse())
                .remarks(item.getRemarks())
                .photoUrls(item.getPhotoUrls())
                .build();
        response.setUuid(item.getUuid());
        response.setTemplateItemUuid(item.getTemplateItem().getUuid());
        return response;
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
