package com.fitouts.schedule.domain;

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
@Table(name = "work_calendar_holiday")
@Getter
@Setter
public class WorkCalendarHoliday {

    @Id
    private UUID uuid;

    @Column(name = "calendar_uuid", nullable = false)
    private UUID calendarUuid;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 120)
    private String name;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
