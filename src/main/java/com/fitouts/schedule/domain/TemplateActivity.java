package com.fitouts.schedule.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "template_activity")
@Getter
@Setter
public class TemplateActivity {

    @Id
    private UUID uuid;

    @Column(name = "template_uuid", nullable = false)
    private UUID templateUuid;

    @Column(name = "activity_code", nullable = false, length = 32)
    private String activityCode;

    @Column(name = "wbs_phase", length = 120)
    private String wbsPhase;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_duration_days", nullable = false)
    private int baseDurationDays = 1;

    /** QUANTITY, PARAMETRIC or FIXED. */
    @Column(name = "scaling_method", nullable = false, length = 16)
    private String scalingMethod = "PARAMETRIC";

    /** Which parameter drives the scaling: AREA, ROOMS, FLOORS or a BOQ quantity code. */
    @Column(name = "scaling_driver", length = 32)
    private String scalingDriver;

    @Column(name = "crew_output_ref", length = 120)
    private String crewOutputRef;

    @Column(name = "trade_package_code", length = 32)
    private String tradePackageCode;

    @Column(name = "trade_label", length = 120)
    private String tradeLabel;

    @Column(name = "is_milestone", nullable = false)
    private boolean milestone;

    @Column(name = "is_critical_seed", nullable = false)
    private boolean criticalSeed;

    @Column(name = "is_locked_duration", nullable = false)
    private boolean lockedDuration;

    /** When set, the activity only exists if the project has this scope toggle on. */
    @Column(name = "scope_toggle_code", length = 48)
    private String scopeToggleCode;

    @Column(name = "constraint_note", columnDefinition = "text")
    private String constraintNote;

    /**
     * The source file's own start/finish day numbers. Not used for scheduling: they disagree
     * with the dependency logic on a third of activities. Kept so the variance is visible and
     * a later correction is a data fix rather than a rebuild.
     */
    @Column(name = "seed_start_wd")
    private Integer seedStartWd;

    @Column(name = "seed_finish_wd")
    private Integer seedFinishWd;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
