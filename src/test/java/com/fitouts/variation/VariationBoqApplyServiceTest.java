package com.fitouts.variation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.variation.application.VariationBoqApplyService;
import com.fitouts.variation.domain.VariationBoqChange;
import com.fitouts.variation.domain.VariationBoqChangeRepository;
import com.fitouts.variation.domain.VariationLine;
import com.fitouts.variation.domain.VariationLineRepository;
import com.fitouts.variation.domain.VariationLineType;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

class VariationBoqApplyServiceTest {
    @Test
    void applyKeepsSourceApprovedAndStoresBoqReferences() {
        BoqProjectRules rules = mock(BoqProjectRules.class);
        BoqDocumentRepository documents = mock(BoqDocumentRepository.class);
        BoqLineRepository boqLines = mock(BoqLineRepository.class);
        VariationLineRepository variationLines = mock(VariationLineRepository.class);
        VariationBoqChangeRepository changes = mock(VariationBoqChangeRepository.class);
        WorkItemRepository workItems = mock(WorkItemRepository.class);
        VariationRequestRepository variations = mock(VariationRequestRepository.class);
        VariationBoqApplyService service = new VariationBoqApplyService(
                rules, documents, boqLines, variationLines, changes, variations, workItems);

        Project project = new Project();
        project.setId(7L);
        BoqDocument source = BoqDocument.builder()
                .id(UUID.randomUUID()).project(project).companyId(UUID.randomUUID())
                .status(BoqDocumentStatus.APPROVED).version("1.0")
                .subtotal(new BigDecimal("100")).vatAmount(new BigDecimal("5"))
                .grandTotal(new BigDecimal("105")).build();
        VariationRequest variation = new VariationRequest();
        variation.setUuid(UUID.randomUUID());
        variation.setProjectId(7L);
        variation.setCrNumber("CR-0001");

        when(changes.findByVariationUuidOrderByCreatedAtAsc(variation.getUuid())).thenReturn(List.of());
        when(rules.findApproved(7L)).thenReturn(Optional.of(source));
        when(rules.nextVersion(7L)).thenReturn("1.1");
        when(variationLines.findByVariationUuidOrderBySortOrderAsc(variation.getUuid())).thenReturn(List.of());
        when(boqLines.findByBoqIdOrderBySortOrderAsc(any())).thenReturn(List.of());
        when(documents.save(any())).thenAnswer(invocation -> {
            BoqDocument document = invocation.getArgument(0);
            if (document.getId() == null) document.setId(UUID.randomUUID());
            return document;
        });

        UUID resultId = service.apply(variation, 99L);

        assertThat(source.getStatus()).isEqualTo(BoqDocumentStatus.APPROVED);
        assertThat(variation.getSourceBoqId()).isEqualTo(source.getId());
        assertThat(variation.getResultBoqId()).isEqualTo(resultId);
    }

    @Test
    void applyNewModifyAndLumpSumLinesCreatesDetailedAudit() {
        BoqProjectRules rules = mock(BoqProjectRules.class);
        BoqDocumentRepository documents = mock(BoqDocumentRepository.class);
        BoqLineRepository boqLines = mock(BoqLineRepository.class);
        VariationLineRepository variationLines = mock(VariationLineRepository.class);
        VariationBoqChangeRepository changes = mock(VariationBoqChangeRepository.class);
        WorkItemRepository workItems = mock(WorkItemRepository.class);
        VariationRequestRepository variations = mock(VariationRequestRepository.class);
        VariationBoqApplyService service = new VariationBoqApplyService(
                rules, documents, boqLines, variationLines, changes, variations, workItems);

        Project project = new Project();
        project.setId(7L);
        BoqDocument source = BoqDocument.builder()
                .id(UUID.randomUUID()).project(project).companyId(UUID.randomUUID())
                .status(BoqDocumentStatus.APPROVED).version("1.0")
                .subtotal(BigDecimal.ZERO).vatAmount(BigDecimal.ZERO)
                .grandTotal(BigDecimal.ZERO).build();
        VariationRequest variation = new VariationRequest();
        variation.setUuid(UUID.randomUUID());
        variation.setProjectId(7L);
        variation.setCrNumber("CR-0002");
        com.fitouts.boq.domain.BoqLine sourceLine = com.fitouts.boq.domain.BoqLine.builder()
                .id(UUID.randomUUID()).boq(source).description("Existing partition").unit("m2")
                .quantity(new BigDecimal("2")).rate(new BigDecimal("20"))
                .amount(new BigDecimal("40")).sortOrder(0).build();
        VariationLine newLine = variationLine(variation, VariationLineType.NEW, null,
                "Partition upgrade", "m2", "4", "25");
        VariationLine modifyLine = variationLine(variation, VariationLineType.MODIFY, sourceLine.getId(),
                "Existing partition revised", "m2", "3", "30");
        VariationLine lumpLine = variationLine(variation, VariationLineType.LUMP_SUM, null,
                "Mobilisation", "ls", "1", "50");

        when(changes.findByVariationUuidOrderByCreatedAtAsc(variation.getUuid())).thenReturn(List.of());
        when(rules.findApproved(7L)).thenReturn(Optional.of(source));
        when(rules.nextVersion(7L)).thenReturn("1.1");
        when(variationLines.findByVariationUuidOrderBySortOrderAsc(variation.getUuid()))
                .thenReturn(List.of(newLine, modifyLine, lumpLine));
        when(boqLines.findByBoqIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(sourceLine), List.of());
        when(documents.save(any())).thenAnswer(invocation -> {
            BoqDocument document = invocation.getArgument(0);
            if (document.getId() == null) document.setId(UUID.randomUUID());
            return document;
        });
        when(boqLines.save(any())).thenAnswer(invocation -> {
            com.fitouts.boq.domain.BoqLine saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        service.apply(variation, 99L);

        ArgumentCaptor<VariationBoqChange> audit = ArgumentCaptor.forClass(VariationBoqChange.class);
        verify(changes, times(3)).save(audit.capture());
        assertThat(audit.getAllValues()).extracting(VariationBoqChange::getChangeType)
                .containsExactly(VariationLineType.NEW, VariationLineType.MODIFY, VariationLineType.LUMP_SUM);
        assertThat(audit.getAllValues().get(0).getDeltaAmount()).isEqualByComparingTo("100.00");
        assertThat(audit.getAllValues().get(1).getPreviousAmount()).isEqualByComparingTo("40");
        assertThat(audit.getAllValues().get(1).getDeltaAmount()).isEqualByComparingTo("50.00");
        assertThat(audit.getAllValues().get(2).getDeltaAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void applyWithoutApprovedBoqFailsBeforeCreatingRevision() {
        BoqProjectRules rules = mock(BoqProjectRules.class);
        BoqDocumentRepository documents = mock(BoqDocumentRepository.class);
        BoqLineRepository boqLines = mock(BoqLineRepository.class);
        VariationLineRepository variationLines = mock(VariationLineRepository.class);
        VariationBoqChangeRepository changes = mock(VariationBoqChangeRepository.class);
        VariationRequestRepository variations = mock(VariationRequestRepository.class);
        VariationBoqApplyService service = new VariationBoqApplyService(
                rules, documents, boqLines, variationLines, changes, variations,
                mock(WorkItemRepository.class));
        VariationRequest variation = new VariationRequest();
        variation.setUuid(UUID.randomUUID());
        variation.setProjectId(7L);
        when(changes.findByVariationUuidOrderByCreatedAtAsc(variation.getUuid())).thenReturn(List.of());
        when(rules.findApproved(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(variation, 99L))
                .hasMessageContaining("No approved BOQ");
    }

    private static VariationLine variationLine(VariationRequest variation, VariationLineType type,
                                               UUID sourceLineId, String description, String unit,
                                               String quantity, String sellRate) {
        VariationLine line = new VariationLine();
        line.setUuid(UUID.randomUUID());
        line.setVariationUuid(variation.getUuid());
        line.setLineType(type);
        line.setSourceBoqLineId(sourceLineId);
        line.setDescription(description);
        line.setUnit(unit);
        line.setQuantity(new BigDecimal(quantity));
        line.setSellRate(new BigDecimal(sellRate));
        return line;
    }
}
