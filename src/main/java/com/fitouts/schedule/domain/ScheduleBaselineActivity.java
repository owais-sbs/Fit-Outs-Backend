package com.fitouts.schedule.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "schedule_baseline_activity")
@Getter
@Setter
public class ScheduleBaselineActivity {

    @Id
    private UUID uuid;

    @Column(name = "baseline_uuid", nullable = false)
    private UUID baselineUuid;

    @Column(name = "activity_uuid", nullable = false)
    private UUID activityUuid;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "percent_complete", nullable = false)
    private int percentComplete;

    @Column(nullable = false)
    private BigDecimal weight = BigDecimal.ONE;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
