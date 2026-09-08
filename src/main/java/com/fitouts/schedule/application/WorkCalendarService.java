package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.schedule.domain.WorkCalendar;
import com.fitouts.schedule.domain.WorkCalendarHoliday;
import com.fitouts.schedule.domain.WorkCalendarHolidayRepository;
import com.fitouts.schedule.domain.WorkCalendarRepository;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.shared.context.CompanyContext;

import lombok.RequiredArgsConstructor;

/**
 * Loads persisted calendars into the engine's {@link WorkingCalendar}.
 *
 * <p>Resolution order is tenant default, then platform default, then a hard-coded UAE week, so
 * a tenant that has never configured a calendar still gets sensible working days rather than
 * an error at schedule time.
 */
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarRepository calendarRepository;
    private final WorkCalendarHolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<WorkCalendar> list() {
        return calendarRepository.findVisible(CompanyContext.get());
    }

    @Transactional(readOnly = true)
    public Optional<WorkCalendar> resolveEntity(UUID calendarUuid) {
        if (calendarUuid != null) {
            Optional<WorkCalendar> explicit = calendarRepository.findById(calendarUuid);
            if (explicit.isPresent()) return explicit;
        }
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            Optional<WorkCalendar> tenantDefault =
                    calendarRepository.findFirstByCompanyIdAndDefaultCalendarTrue(companyId);
            if (tenantDefault.isPresent()) return tenantDefault;
        }
        return calendarRepository.findFirstByCompanyIdIsNullAndDefaultCalendarTrue();
    }

    @Transactional(readOnly = true)
    public WorkingCalendar resolve(UUID calendarUuid) {
        return resolveEntity(calendarUuid)
                .map(this::toEngineCalendar)
                .orElseGet(WorkingCalendar::uaeDefault);
    }

    public WorkingCalendar toEngineCalendar(WorkCalendar calendar) {
        Set<LocalDate> holidays = new HashSet<>();
        for (WorkCalendarHoliday h : holidayRepository.findByCalendarUuid(calendar.getUuid())) {
            holidays.add(h.getHolidayDate());
        }
        return new WorkingCalendar(WorkingCalendar.parseWorkingDays(calendar.getWorkingDays()), holidays);
    }
}
