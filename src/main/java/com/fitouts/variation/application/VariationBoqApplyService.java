package com.fitouts.variation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.variation.domain.VariationBoqChange;
import com.fitouts.variation.domain.VariationBoqChangeRepository;
import com.fitouts.variation.domain.VariationLine;
import com.fitouts.variation.domain.VariationLineRepository;
import com.fitouts.variation.domain.VariationLineType;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VariationBoqApplyService {
    private final BoqProjectRules boqProjectRules;
    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final VariationLineRepository variationLineRepository;
    private final VariationBoqChangeRepository changeRepository;
    private final VariationRequestRepository variationRequestRepository;
    private final WorkItemRepository workItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID apply(VariationRequest variation, Long actorId) {
        if (changeRepository.findByVariationUuidOrderByCreatedAtAsc(variation.getUuid()).stream().findFirst().isPresent()) {
            throw new BadRequestException("Variation BOQ changes have already been applied");
        }
        BoqDocument source = boqProjectRules.findApproved(variation.getProjectId())
                .orElseThrow(() -> new BadRequestException("No approved BOQ exists for this project"));
        List<VariationLine> changes =
                variationLineRepository.findByVariationUuidOrderBySortOrderAsc(variation.getUuid());

        BoqDocument result = cloneDocument(source, variation);
        result = boqDocumentRepository.save(result);

        Map<UUID, BoqLine> clonedBySourceId = new HashMap<>();
        int nextSortOrder = 0;
        for (BoqLine sourceLine : boqLineRepository.findByBoqIdOrderBySortOrderAsc(source.getId())) {
            BoqLine cloned = cloneLine(sourceLine, result);
            cloned = boqLineRepository.save(cloned);
            clonedBySourceId.put(sourceLine.getId(), cloned);
            nextSortOrder = Math.max(nextSortOrder, cloned.getSortOrder() + 1);
        }

        for (VariationLine line : changes) {
            if (line.getLineType() == VariationLineType.MODIFY) {
                applyModify(source, result, line, clonedBySourceId, actorId);
            } else {
                applyNew(source, result, line, nextSortOrder++, actorId);
            }
        }

        recalculate(result, source);
        boqDocumentRepository.save(result);
        variation.setSourceBoqId(source.getId());
        variation.setResultBoqId(result.getId());
        variationRequestRepository.save(variation);
        return result.getId();
    }

    private BoqDocument cloneDocument(BoqDocument source, VariationRequest variation) {
        return BoqDocument.builder()
                .project(source.getProject())
                .companyId(source.getCompanyId())
                .qtoSession(source.getQtoSession())
                .version(boqProjectRules.nextVersion(variation.getProjectId()))
                .parentBoq(source.getParentBoq() != null ? source.getParentBoq() : source)
                .revisionLabel(variation.getCrNumber())
                .status(BoqDocumentStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .approvedBy(variation.getApprovedBy())
                .notes("Applied approved variation " + variation.getCrNumber())
                .subtotal(source.getSubtotal())
                .vatAmount(source.getVatAmount())
                .grandTotal(source.getGrandTotal())
                .build();
    }

    private BoqLine cloneLine(BoqLine source, BoqDocument result) {
        return BoqLine.builder()
                .boq(result)
                .categoryCode(source.getCategoryCode())
                .categoryName(source.getCategoryName())
                .description(source.getDescription())
                .unit(source.getUnit())
                .quantity(source.getQuantity())
                .rate(source.getRate())
                .amount(source.getAmount())
                .qtoLine(source.getQtoLine())
                .workItem(source.getWorkItem())
                .floorLabel(source.getFloorLabel())
                .roomLabel(source.getRoomLabel())
                .sortOrder(source.getSortOrder())
                .source(source.getSource())
                .build();
    }

    private void applyModify(BoqDocument source, BoqDocument result, VariationLine change,
                             Map<UUID, BoqLine> clonedBySourceId, Long actorId) {
        if (change.getSourceBoqLineId() == null) {
            throw new BadRequestException("MODIFY line requires sourceBoqLineId");
        }
        BoqLine target = clonedBySourceId.get(change.getSourceBoqLineId());
        if (target == null) {
            throw new BadRequestException("Source BOQ line is not part of the approved BOQ");
        }
        BigDecimal oldQuantity = target.getQuantity();
        BigDecimal oldRate = target.getRate();
        BigDecimal oldAmount = target.getAmount();
        target.setDescription(hasText(change.getDescription()) ? change.getDescription() : target.getDescription());
        target.setUnit(hasText(change.getUnit()) ? change.getUnit() : target.getUnit());
        target.setQuantity(value(change.getQuantity()));
        target.setRate(value(change.getSellRate()));
        target.setAmount(amount(target.getQuantity(), target.getRate()));
        if (change.getWorkItemId() != null) {
            target.setWorkItem(workItemRepository.getReferenceById(change.getWorkItemId()));
        }
        target.setSource("VARIATION");
        boqLineRepository.save(target);
        audit(source, result, change, target, oldQuantity, oldRate, oldAmount, actorId);
    }

    private void applyNew(BoqDocument source, BoqDocument result, VariationLine change,
                          int sortOrder, Long actorId) {
        boolean lumpSum = change.getLineType() == VariationLineType.LUMP_SUM;
        BoqLine target = BoqLine.builder()
                .boq(result)
                .categoryCode(lumpSum ? "VARIATION" : null)
                .categoryName(lumpSum ? "Variation" : null)
                .description(change.getDescription())
                .unit(hasText(change.getUnit()) ? change.getUnit() : (lumpSum ? "ls" : null))
                .quantity(value(change.getQuantity()))
                .rate(value(change.getSellRate()))
                .amount(amount(change.getQuantity(), change.getSellRate()))
                .workItem(change.getWorkItemId() == null ? null
                        : workItemRepository.getReferenceById(change.getWorkItemId()))
                .sortOrder(sortOrder)
                .source("VARIATION")
                .build();
        target = boqLineRepository.save(target);
        audit(source, result, change, target, null, null, null, actorId);
    }

    private void audit(BoqDocument source, BoqDocument result, VariationLine line, BoqLine target,
                       BigDecimal oldQuantity, BigDecimal oldRate, BigDecimal oldAmount, Long actorId) {
        VariationBoqChange audit = new VariationBoqChange();
        audit.setVariationUuid(line.getVariationUuid());
        audit.setVariationLineUuid(line.getUuid());
        audit.setSourceBoqUuid(source.getId());
        audit.setResultingBoqUuid(result.getId());
        audit.setSourceBoqLineId(line.getSourceBoqLineId());
        audit.setResultingBoqLineId(target.getId());
        audit.setChangeType(line.getLineType());
        audit.setDescription(target.getDescription());
        audit.setUnit(target.getUnit());
        audit.setPreviousQuantity(oldQuantity);
        audit.setNewQuantity(target.getQuantity());
        audit.setPreviousRate(oldRate);
        audit.setNewRate(target.getRate());
        audit.setPreviousAmount(oldAmount);
        audit.setNewAmount(target.getAmount());
        audit.setDeltaAmount(target.getAmount().subtract(value(oldAmount)));
        audit.setAppliedBy(actorId);
        changeRepository.save(audit);
    }

    private void recalculate(BoqDocument result, BoqDocument source) {
        BigDecimal subtotal = boqLineRepository.findByBoqIdOrderBySortOrderAsc(result.getId()).stream()
                .map(BoqLine::getAmount).map(VariationBoqApplyService::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vatRate = BigDecimal.ZERO;
        if (source.getSubtotal() != null && source.getSubtotal().signum() != 0 && source.getVatAmount() != null) {
            vatRate = source.getVatAmount().divide(source.getSubtotal(), 8, RoundingMode.HALF_UP);
        }
        BigDecimal vat = subtotal.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        result.setSubtotal(subtotal);
        result.setVatAmount(vat);
        result.setGrandTotal(subtotal.add(vat));
    }

    private static BigDecimal amount(BigDecimal quantity, BigDecimal rate) {
        return value(quantity).multiply(value(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
