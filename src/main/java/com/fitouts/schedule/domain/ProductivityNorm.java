package com.fitouts.schedule.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Crew output per day for a work item, used by the quantity-driven duration scaler. */
@Entity
@Table(name = "productivity_norm")
@Getter
@Setter
public class ProductivityNorm {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "work_item", nullable = false)
    private String workItem;

    @Column(length = 32)
    private String unit;

    @Column(name = "output_per_crew_per_day_min", precision = 12, scale = 3)
    private BigDecimal outputPerCrewPerDayMin;

    @Column(name = "output_per_crew_per_day_max", precision = 12, scale = 3)
    private BigDecimal outputPerCrewPerDayMax;

    @Column(name = "output_raw", length = 64)
    private String outputRaw;

    @Column(name = "standard_crew")
    private String standardCrew;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "match_keywords", columnDefinition = "text")
    private String matchKeywords;

    /** Midpoint of the published range; the seed quotes outputs as "30-40" rather than a point. */
    public Double midOutput() {
        if (outputPerCrewPerDayMin != null && outputPerCrewPerDayMax != null) {
            return outputPerCrewPerDayMin.add(outputPerCrewPerDayMax)
                    .divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP).doubleValue();
        }
        if (outputPerCrewPerDayMin != null) return outputPerCrewPerDayMin.doubleValue();
        if (outputPerCrewPerDayMax != null) return outputPerCrewPerDayMax.doubleValue();
        return null;
    }

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
