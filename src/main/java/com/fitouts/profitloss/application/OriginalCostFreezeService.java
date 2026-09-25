package com.fitouts.profitloss.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.workitemconfiguration.domain.WorkItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OriginalCostFreezeService {

    private final ProjectCommercialRepository commercialRepository;
    private final BoqLineRepository boqLineRepository;

    /**
     * Freeze original estimated cost once at first BOQ approval.
     * Uses work-item costPrice × quantity where linked; no-op if already set or no cost data.
     */
    @Transactional
    public void freezeIfAbsent(BoqDocument approvedBoq) {
        if (approvedBoq == null || approvedBoq.getProject() == null) {
            return;
        }
        Long projectId = approvedBoq.getProject().getId();
        UUID companyId = approvedBoq.getCompanyId() != null
                ? approvedBoq.getCompanyId()
                : CompanyContext.get();
        if (companyId == null) {
            return;
        }

        ProjectCommercial commercial = commercialRepository
                .findFirstByProjectIdAndCompanyIdOrderByUuidAsc(projectId, companyId)
                .orElseGet(() -> {
                    ProjectCommercial created = new ProjectCommercial();
                    created.setProjectId(projectId);
                    created.setCompanyId(companyId);
                    BigDecimal contract = approvedBoq.getGrandTotal() != null
                            ? approvedBoq.getGrandTotal()
                            : BigDecimal.ZERO;
                    created.setOriginalContractValue(contract);
                    created.setCurrentContractValue(contract);
                    created.setCurrentCost(BigDecimal.ZERO);
                    created.setCurrentMargin(contract);
                    return commercialRepository.save(created);
                });

        if (commercial.getOriginalCost() != null) {
            return;
        }

        List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(approvedBoq.getId());
        BigDecimal estimated = BigDecimal.ZERO;
        boolean anyCost = false;
        for (BoqLine line : lines) {
            WorkItem workItem = line.getWorkItem();
            if (workItem == null || workItem.getCostPrice() == null) {
                continue;
            }
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            estimated = estimated.add(workItem.getCostPrice().multiply(qty));
            anyCost = true;
        }
        if (!anyCost) {
            log.info("No work-item cost data to freeze for project {}", projectId);
            return;
        }
        commercial.setOriginalCost(estimated.setScale(2, RoundingMode.HALF_UP));
        if (commercial.getOriginalContractValue() == null
                || commercial.getOriginalContractValue().signum() == 0) {
            BigDecimal contract = approvedBoq.getGrandTotal() != null
                    ? approvedBoq.getGrandTotal()
                    : commercial.getCurrentContractValue();
            commercial.setOriginalContractValue(contract != null ? contract : BigDecimal.ZERO);
        }
        commercialRepository.save(commercial);
        log.info("Froze originalCost={} for project {}", commercial.getOriginalCost(), projectId);
    }
}
