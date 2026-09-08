package com.fitouts.schedule.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Working-day arithmetic for the CPM engine.
 *
 * <p>Every duration and lag in the schedule is expressed in working days. The calendar is the
 * only thing that knows what a working day is, so all date maths goes through here rather than
 * being reimplemented with {@code plusDays} at each call site.
 *
 * <p>UAE default is Saturday through Thursday. The summer midday break shortens the day; it does
 * not remove it, so it deliberately has no effect on the arithmetic.
 */
public final class WorkingCalendar {

    private static final int MAX_SKIP_DAYS = 3650;

    private final Set<DayOfWeek> workingDays;
    private final Set<LocalDate> holidays;

    public WorkingCalendar(Set<DayOfWeek> workingDays, Set<LocalDate> holidays) {
        if (workingDays == null || workingDays.isEmpty()) {
            throw new IllegalArgumentException("A calendar needs at least one working day");
        }
        this.workingDays = Collections.unmodifiableSet(EnumSet.copyOf(workingDays));
        this.holidays = Collections.unmodifiableSet(new HashSet<>(holidays == null ? Set.of() : holidays));
    }

    /** UAE standard six-day week, Saturday to Thursday. */
    public static WorkingCalendar uaeDefault() {
        return new WorkingCalendar(
                EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                Set.of());
    }

    /**
     * Parses the persisted {@code "6,7,1,2,3,4"} form (ISO day numbers, Monday = 1).
     */
    public static Set<DayOfWeek> parseWorkingDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (csv != null) {
            for (String part : csv.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    days.add(DayOfWeek.of(Integer.parseInt(trimmed)));
                } catch (NumberFormatException | java.time.DateTimeException ignored) {
                    // A malformed calendar row should not take the scheduler down.
                }
            }
        }
        if (days.isEmpty()) {
            days.addAll(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY));
        }
        return days;
    }

    public boolean isWorkingDay(LocalDate date) {
        return workingDays.contains(date.getDayOfWeek()) && !holidays.contains(date);
    }

    public Set<DayOfWeek> workingDays() {
        return workingDays;
    }

    public Set<LocalDate> holidays() {
        return holidays;
    }

    /** The given date if it works, otherwise the next working day after it. */
    public LocalDate nextWorkingDay(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SKIP_DAYS; i++) {
            if (isWorkingDay(cursor)) return cursor;
            cursor = cursor.plusDays(1);
        }
        throw new IllegalStateException("No working day found within ten years of " + date);
    }

    /** The given date if it works, otherwise the last working day before it. */
    public LocalDate previousWorkingDay(LocalDate date) {
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SKIP_DAYS; i++) {
            if (isWorkingDay(cursor)) return cursor;
            cursor = cursor.minusDays(1);
        }
        throw new IllegalStateException("No working day found within ten years before " + date);
    }

    /**
     * The finish date of an activity that starts on {@code start} and runs for
     * {@code durationWorkingDays} working days, inclusive of both ends. A one-day activity
     * starts and finishes on the same day.
     */
    public LocalDate finishOf(LocalDate start, int durationWorkingDays) {
        LocalDate first = nextWorkingDay(start);
        if (durationWorkingDays <= 1) return first;
        return addWorkingDays(first, durationWorkingDays - 1);
    }

    /**
     * The start date of an activity that finishes on {@code finish} and runs for
     * {@code durationWorkingDays} working days, inclusive.
     */
    public LocalDate startOf(LocalDate finish, int durationWorkingDays) {
        LocalDate last = previousWorkingDay(finish);
        if (durationWorkingDays <= 1) return last;
        return subtractWorkingDays(last, durationWorkingDays - 1);
    }

    /** Moves {@code days} working days forward from a date, skipping non-working days. */
    public LocalDate addWorkingDays(LocalDate from, int days) {
        if (days < 0) return subtractWorkingDays(from, -days);
        LocalDate cursor = nextWorkingDay(from);
        int remaining = days;
        while (remaining > 0) {
            cursor = nextWorkingDay(cursor.plusDays(1));
            remaining--;
        }
        return cursor;
    }

    /** Moves {@code days} working days backward from a date. */
    public LocalDate subtractWorkingDays(LocalDate from, int days) {
        if (days < 0) return addWorkingDays(from, -days);
        LocalDate cursor = previousWorkingDay(from);
        int remaining = days;
        while (remaining > 0) {
            cursor = previousWorkingDay(cursor.minusDays(1));
            remaining--;
        }
        return cursor;
    }

    /** Inclusive working-day count between two dates. Returns 0 when {@code to} precedes {@code from}. */
    public int workingDaysBetweenInclusive(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return 0;
        int count = 0;
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            if (isWorkingDay(cursor)) count++;
        }
        return count;
    }

    /**
     * Signed working-day difference used for float. Positive when {@code to} is later.
     */
    public int floatBetween(LocalDate early, LocalDate late) {
        if (early == null || late == null) return 0;
        if (late.isBefore(early)) {
            return -(workingDaysBetweenInclusive(late, early) - 1);
        }
        int inclusive = workingDaysBetweenInclusive(early, late);
        return inclusive == 0 ? 0 : inclusive - 1;
    }
}
