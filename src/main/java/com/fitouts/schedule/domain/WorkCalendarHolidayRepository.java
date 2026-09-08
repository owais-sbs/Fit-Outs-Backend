package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkCalendarHolidayRepository extends JpaRepository<WorkCalendarHoliday, UUID> {

    List<WorkCalendarHoliday> findByCalendarUuid(UUID calendarUuid);
}
