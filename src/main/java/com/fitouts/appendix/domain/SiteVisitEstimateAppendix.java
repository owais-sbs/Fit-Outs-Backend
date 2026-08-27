package com.fitouts.appendix.domain;

import java.util.UUID;

import com.fitouts.checklist.domain.SiteVisitEstimate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_visit_estimate_appendices")
@IdClass(SiteVisitEstimateAppendixId.class)
@Getter
@Setter
public class SiteVisitEstimateAppendix {

    @Id
    @Column(name = "estimate_uuid")
    private UUID estimateUuid;

    @Id
    @Column(name = "appendix_master_uuid")
    private UUID appendixMasterUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_uuid", insertable = false, updatable = false)
    private SiteVisitEstimate estimate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appendix_master_uuid", insertable = false, updatable = false)
    private AppendixMaster appendixMaster;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
