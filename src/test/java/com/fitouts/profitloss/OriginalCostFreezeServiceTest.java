package com.fitouts.profitloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.profitloss.application.OriginalCostFreezeService;
import com.fitouts.project.domain.Project;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.workitemconfiguration.domain.WorkItem;

class OriginalCostFreezeServiceTest {

    @Test
    @DisplayName("freezes originalCost once from work-item cost prices")
    void freezeOnce() {
        ProjectCommercialRepository commercialRepository = mock(ProjectCommercialRepository.class);
        BoqLineRepository boqLineRepository = mock(BoqLineRepository.class);
        OriginalCostFreezeService service =
                new OriginalCostFreezeService(commercialRepository, boqLineRepository);

        UUID companyId = UUID.randomUUID();
        UUID boqId = UUID.randomUUID();
        Project project = new Project();
        project.setId(10L);
        project.setCompanyId(companyId);

        BoqDocument boq = BoqDocument.builder()
                .id(boqId)
                .project(project)
                .companyId(companyId)
                .grandTotal(new BigDecimal("500000"))
                .build();

        ProjectCommercial commercial = new ProjectCommercial();
        commercial.setProjectId(10L);
        commercial.setCompanyId(companyId);
        commercial.setOriginalContractValue(new BigDecimal("500000"));
        commercial.setCurrentContractValue(new BigDecimal("500000"));
        when(commercialRepository.findByProjectIdAndCompanyId(10L, companyId))
                .thenReturn(Optional.of(commercial));

        WorkItem wi = new WorkItem();
        wi.setCostPrice(new BigDecimal("100"));
        BoqLine line = BoqLine.builder()
                .quantity(new BigDecimal("10"))
                .workItem(wi)
                .build();
        when(boqLineRepository.findByBoqIdOrderBySortOrderAsc(boqId)).thenReturn(List.of(line));
        when(commercialRepository.save(commercial)).thenReturn(commercial);

        service.freezeIfAbsent(boq);
        assertThat(commercial.getOriginalCost()).isEqualByComparingTo("1000");

        commercial.setOriginalCost(new BigDecimal("1000"));
        service.freezeIfAbsent(boq);
        verify(boqLineRepository, org.mockito.Mockito.times(1))
                .findByBoqIdOrderBySortOrderAsc(boqId);
    }

    @Test
    @DisplayName("does not overwrite existing originalCost")
    void doesNotOverwrite() {
        ProjectCommercialRepository commercialRepository = mock(ProjectCommercialRepository.class);
        BoqLineRepository boqLineRepository = mock(BoqLineRepository.class);
        OriginalCostFreezeService service =
                new OriginalCostFreezeService(commercialRepository, boqLineRepository);

        UUID companyId = UUID.randomUUID();
        Project project = new Project();
        project.setId(10L);
        project.setCompanyId(companyId);
        BoqDocument boq = BoqDocument.builder()
                .id(UUID.randomUUID())
                .project(project)
                .companyId(companyId)
                .build();

        ProjectCommercial commercial = new ProjectCommercial();
        commercial.setOriginalCost(new BigDecimal("999"));
        when(commercialRepository.findByProjectIdAndCompanyId(10L, companyId))
                .thenReturn(Optional.of(commercial));

        service.freezeIfAbsent(boq);
        verify(boqLineRepository, never()).findByBoqIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.any());
        assertThat(commercial.getOriginalCost()).isEqualByComparingTo("999");
    }
}
