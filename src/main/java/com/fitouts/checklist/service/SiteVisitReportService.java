package com.fitouts.checklist.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.checklist.domain.ChecklistTemplateItem;
import com.fitouts.checklist.domain.SiteVisit;
import com.fitouts.checklist.domain.SiteVisitReport;
import com.fitouts.checklist.domain.SiteVisitStatus;
import com.fitouts.checklist.dto.SiteVisitReportItemRequest;
import com.fitouts.checklist.dto.SiteVisitReportRequest;
import com.fitouts.checklist.dto.SiteVisitReportResponse;
import com.fitouts.checklist.mapper.SiteVisitReportMapper;
import com.fitouts.checklist.repository.SiteVisitReportRepository;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitReportService {

    private final SiteVisitReportRepository repository;
    private final SiteVisitService siteVisitService;
    private final SiteVisitReportMapper mapper;

    @Transactional
    public SiteVisitReportResponse submit(UUID siteVisitUuid, SiteVisitReportRequest request) {
        SiteVisit siteVisit = siteVisitService.getSiteVisit(siteVisitUuid);
        if (repository.existsBySiteVisitUuid(siteVisitUuid)) {
            throw new ConflictException("Site visit report already exists");
        }

        Map<UUID, ChecklistTemplateItem> templateItems = templateItemMap(siteVisit);
        validateItems(request.getItems(), templateItems);

        SiteVisitReport report = mapper.toEntity(request, siteVisit);
        request.getItems().forEach(item -> report.addItem(mapper.toItemEntity(
                item,
                templateItems.get(item.getTemplateItemUuid()),
                normalizePhotoUrls(item.getPhotoUrls()))));

        siteVisit.setStatus(SiteVisitStatus.COMPLETED);
        return mapper.toResponse(repository.save(report));
    }

    private Map<UUID, ChecklistTemplateItem> templateItemMap(SiteVisit siteVisit) {
        Map<UUID, ChecklistTemplateItem> items = new HashMap<>();
        siteVisit.getChecklistTemplate().getItems().forEach(item -> items.put(item.getUuid(), item));
        return items;
    }

    private void validateItems(List<SiteVisitReportItemRequest> items, Map<UUID, ChecklistTemplateItem> templateItems) {
        Set<UUID> submittedUuids = new HashSet<>();
        Set<UUID> answeredUuids = new HashSet<>();

        for (SiteVisitReportItemRequest item : items) {
            if (!templateItems.containsKey(item.getTemplateItemUuid())) {
                throw new BadRequestException("Report item does not belong to the site visit checklist template");
            }
            if (!submittedUuids.add(item.getTemplateItemUuid())) {
                throw new BadRequestException("Checklist item can only be answered once");
            }
            if (hasAnswer(item)) {
                answeredUuids.add(item.getTemplateItemUuid());
            }
        }

        boolean missingRequiredAnswer = templateItems.values().stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsRequired()))
                .map(ChecklistTemplateItem::getUuid)
                .anyMatch(itemUuid -> !answeredUuids.contains(itemUuid));
        if (missingRequiredAnswer) {
            throw new BadRequestException("All required checklist items must be answered");
        }
    }

    private boolean hasAnswer(SiteVisitReportItemRequest item) {
        return (item.getResponse() != null && !item.getResponse().isBlank())
                || !normalizePhotoUrls(item.getPhotoUrls()).isEmpty();
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
