package com.fitouts.appendix.domain;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SiteVisitEstimateAppendixId implements Serializable {
    private UUID estimateUuid;
    private UUID appendixMasterUuid;
}
