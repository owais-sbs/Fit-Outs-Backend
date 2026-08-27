package com.fitouts.appendix.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.appendix.domain.SiteVisitEstimateAppendix;
import com.fitouts.appendix.dto.AppendixMasterResponse;
import com.fitouts.appendix.repository.SiteVisitEstimateAppendixRepository;
import com.fitouts.checklist.domain.SiteVisitEstimate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitEstimateAppendixService {

    private final SiteVisitEstimateAppendixRepository repository;
    private final AppendixMasterService appendixMasterService;

    @Transactional
    public void syncSelections(SiteVisitEstimate estimate, List<UUID> appendixIds) {
        repository.deleteByEstimateUuid(estimate.getUuid());
        if (appendixIds == null || appendixIds.isEmpty()) {
            return;
        }
        int order = 0;
        for (UUID appendixId : appendixIds) {
            if (appendixId == null) continue;
            SiteVisitEstimateAppendix link = new SiteVisitEstimateAppendix();
            link.setEstimateUuid(estimate.getUuid());
            link.setAppendixMasterUuid(appendixId);
            link.setDisplayOrder(order++);
            repository.save(link);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> getSelectedIds(UUID estimateUuid) {
        return repository.findByEstimateUuidOrderByDisplayOrderAsc(estimateUuid).stream()
                .map(SiteVisitEstimateAppendix::getAppendixMasterUuid)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppendixMasterResponse> getSelectedAppendices(UUID estimateUuid) {
        List<UUID> ids = getSelectedIds(estimateUuid);
        return appendixMasterService.getByIds(ids);
    }
}
