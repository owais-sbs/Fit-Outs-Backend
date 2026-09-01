package com.fitouts.checklist.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitReport;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.dto.SiteVisitReportRequest;
import com.fitouts.checklist.dto.SiteVisitReportResponse;
import com.fitouts.checklist.mapper.SiteVisitReportMapper;
import com.fitouts.checklist.repository.SiteVisitReportRepository;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitReportService {

    private final SiteVisitReportRepository repository;
    private final SiteVisitService siteVisitService;
    private final SiteVisitReportMapper mapper;
    private final SiteVisitRecordingService recordingService;

    @Transactional(readOnly = true)
    public SiteVisitReportResponse getBySiteVisitUuid(UUID siteVisitUuid) {
        SiteVisitReport report = repository.findBySiteVisitUuid(siteVisitUuid)
                .orElseThrow(() -> new NotFoundException("Site visit report not found"));
        SiteVisitReportResponse response = mapper.toResponse(report);
        response.setRecordings(recordingService.listByVisit(siteVisitUuid));
        return response;
    }

    @Transactional
    public SiteVisitReportResponse submit(UUID siteVisitUuid, SiteVisitReportRequest request) {
        SiteVisit siteVisit = siteVisitService.getSiteVisit(siteVisitUuid);
        if (repository.existsBySiteVisitUuid(siteVisitUuid)) {
            throw new ConflictException("Site visit report already exists");
        }

        SiteVisitReport report = mapper.toEntity(request, siteVisit);
        request.getItems().forEach(item ->
                report.addItem(mapper.toItemEntity(item, normalizePhotoUrls(item.getPhotoUrls()))));

        siteVisit.setStatus(SiteVisitStatus.COMPLETED);

        SiteVisitReportResponse response = mapper.toResponse(repository.save(report));
        response.setRecordings(recordingService.listByVisit(siteVisitUuid));
        recordingService.processPendingForVisit(siteVisitUuid);
        return response;
    }

    private List<String> normalizePhotoUrls(List<String> photoUrls) {
        if (photoUrls == null) {
            return List.of();
        }
        return photoUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .toList();
    }
}
