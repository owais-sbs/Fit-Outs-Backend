package com.fitouts.schedule.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * <p>UAE default is Saturday through Thursday. When summer midday break is enabled, each working
 * day inside the summer window contributes {@code 5.5/8} of a normal day (midday removes 2.5h
 * from an 8h day), so the same duration finishes later in summer than outside it.
 */
public final class WorkingCalendar {

    private static final int MAX_SKIP_DAYS = 3650;
    /** Midday removes 2.5h from an 8h day → 5.5 productive hours. */
    public static final double SUMMER_DAY_FRACTION = 5.5 / 8.0;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final Set<DayOfWeek> workingDays;
    private final Set<LocalDate> holidays;
    private final boolean summerBreakEnabled;
    private final MonthDay summerBreakStart;
    private final MonthDay summerBreakEnd;

    public WorkingCalendar(Set<DayOfWeek> workingDays, Set<LocalDate> holidays) {
        this(workingDays, holidays, false, null, null);
    }

    public WorkingCalendar(Set<DayOfWeek> workingDays, Set<LocalDate> holidays,
                           boolean summerBreakEnabled, String summerBreakStart, String summerBreakEnd) {
        if (workingDays == null || workingDays.isEmpty()) {
            throw new IllegalArgumentException("A calendar needs at least one working day");
        }
        this.workingDays = Collections.unmodifiableSet(EnumSet.copyOf(workingDays));
        this.holidays = Collections.unmodifiableSet(new HashSet<>(holidays == null ? Set.of() : holidays));
        this.summerBreakEnabled = summerBreakEnabled;
        this.summerBreakStart = parseMonthDay(summerBreakStart);
        this.summerBreakEnd = parseMonthDay(summerBreakEnd);
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

    public boolean isSummerBreakEnabled() {
        return summerBreakEnabled;
    }

    /**
     * Productive fraction of a working day. Non-working days contribute 0; summer midday days
     * contribute {@link #SUMMER_DAY_FRACTION}; otherwise 1.
     */
    public double dayCapacity(LocalDate date) {
        if (!isWorkingDay(date)) return 0;
        return inSummerWindow(date) ? SUMMER_DAY_FRACTION : 1.0;
    }

    public boolean inSummerWindow(LocalDate date) {
        if (!summerBreakEnabled || summerBreakStart == null || summerBreakEnd == null || date == null) {
            return false;
        }
        MonthDay md = MonthDay.from(date);
        if (!summerBreakStart.isAfter(summerBreakEnd)) {
            return !md.isBefore(summerBreakStart) && !md.isAfter(summerBreakEnd);
        }
        // Window wraps year-end (e.g. Nov–Feb).
        return !md.isBefore(summerBreakStart) || !md.isAfter(summerBreakEnd);
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
     * starts and finishes on the same day. Summer midday days consume less capacity, so the
     * same duration spans more calendar working days.
     */
    public LocalDate finishOf(LocalDate start, int durationWorkingDays) {
        if (durationWorkingDays <= 0) return nextWorkingDay(start);
        LocalDate cursor = nextWorkingDay(start);
        double remaining = durationWorkingDays - dayCapacity(cursor);
        int guard = 0;
        while (remaining > 1e-9 && guard++ < MAX_SKIP_DAYS) {
            cursor = nextWorkingDay(cursor.plusDays(1));
            remaining -= dayCapacity(cursor);
        }
        return cursor;
    }

    /**
     * The start date of an activity that finishes on {@code finish} and runs for
     * {@code durationWorkingDays} working days, inclusive.
     */
    public LocalDate startOf(LocalDate finish, int durationWorkingDays) {
        if (durationWorkingDays <= 0) return previousWorkingDay(finish);
        LocalDate cursor = previousWorkingDay(finish);
        double remaining = durationWorkingDays - dayCapacity(cursor);
        int guard = 0;
        while (remaining > 1e-9 && guard++ < MAX_SKIP_DAYS) {
            cursor = previousWorkingDay(cursor.minusDays(1));
            remaining -= dayCapacity(cursor);
        }
        return cursor;
    }

    /** Moves {@code days} working days forward from a date, skipping non-working days. */
    public LocalDate addWorkingDays(LocalDate from, int days) {
        if (days < 0) return subtractWorkingDays(from, -days);
        LocalDate cursor = nextWorkingDay(from);
        double remaining = days;
        int guard = 0;
        while (remaining > 1e-9 && guard++ < MAX_SKIP_DAYS) {
            cursor = nextWorkingDay(cursor.plusDays(1));
            remaining -= dayCapacity(cursor);
        }
        return cursor;
    }

    /** Moves {@code days} working days backward from a date. */
    public LocalDate subtractWorkingDays(LocalDate from, int days) {
        if (days < 0) return addWorkingDays(from, -days);
        LocalDate cursor = previousWorkingDay(from);
        double remaining = days;
        int guard = 0;
        while (remaining > 1e-9 && guard++ < MAX_SKIP_DAYS) {
            cursor = previousWorkingDay(cursor.minusDays(1));
            remaining -= dayCapacity(cursor);
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

    private static MonthDay parseMonthDay(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return MonthDay.parse(value.trim(), MD);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
