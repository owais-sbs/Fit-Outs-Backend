package com.fitouts.schedule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The engine's contract, expressed as the behaviours a programme manager would check by hand.
 */
class CpmEngineTest {

    private final CpmEngine engine = new CpmEngine();

    /** Saturday to Thursday, no holidays. Friday is the only weekend day. */
    private final WorkingCalendar uae = WorkingCalendar.uaeDefault();

    /** 2026-01-03 is a Saturday, so the programme starts on a working day. */
    private static final LocalDate SATURDAY = LocalDate.of(2026, 1, 3);

    private CpmActivity activity(String code, int duration) {
        CpmActivity a = new CpmActivity();
        a.setCode(code);
        a.setName(code);
        a.setDurationWorkingDays(duration);
        return a;
    }

    // -------------------------------------------------------------- calendar

    @Test
    @DisplayName("A five-day activity starting Saturday finishes Wednesday, inclusive of both ends")
    void inclusiveDuration() {
        CpmResult result = engine.solve(List.of(activity("A", 5)), List.of(), SATURDAY, uae);
        CpmActivity a = result.getActivities().get(0);

        assertThat(a.getEarlyStart()).isEqualTo(SATURDAY);
        assertThat(a.getEarlyFinish()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(result.getTotalWorkingDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("Friday is skipped, so a seven-day activity spans eight calendar days")
    void weekendIsNotCounted() {
        CpmResult result = engine.solve(List.of(activity("A", 7)), List.of(), SATURDAY, uae);
        CpmActivity a = result.getActivities().get(0);

        // Sat 3rd through Thu 8th is six days; Friday 9th is off; the seventh day is Sat 10th.
        assertThat(a.getEarlyFinish()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(uae.isWorkingDay(LocalDate.of(2026, 1, 9))).isFalse();
    }

    @Test
    @DisplayName("A holiday on the calendar pushes the finish out by one working day")
    void holidayExtendsTheProgramme() {
        WorkingCalendar withEid = new WorkingCalendar(
                uae.workingDays(), Set.of(LocalDate.of(2026, 1, 5)));

        CpmResult baseline = engine.solve(List.of(activity("A", 5)), List.of(), SATURDAY, uae);
        CpmResult withHoliday = engine.solve(List.of(activity("A", 5)), List.of(), SATURDAY, withEid);

        assertThat(withHoliday.getProjectFinish())
                .isEqualTo(baseline.getProjectFinish().plusDays(1));
    }

    @Test
    @DisplayName("Summer midday (5.5/8 day) makes the same duration finish later than outside summer")
    void summerMiddayExtendsFinish() {
        WorkingCalendar summer = new WorkingCalendar(
                uae.workingDays(), Set.of(), true, "06-15", "09-15");

        LocalDate winterStart = LocalDate.of(2026, 1, 3); // Saturday, outside summer
        LocalDate summerStart = LocalDate.of(2026, 7, 4); // Saturday, inside summer

        LocalDate winterFinish = summer.finishOf(winterStart, 8);
        LocalDate summerFinish = summer.finishOf(summerStart, 8);

        assertThat(summer.inSummerWindow(summerStart)).isTrue();
        assertThat(summer.inSummerWindow(winterStart)).isFalse();
        assertThat(summer.dayCapacity(summerStart)).isEqualTo(WorkingCalendar.SUMMER_DAY_FRACTION);
        assertThat(summer.dayCapacity(winterStart)).isEqualTo(1.0);

        int winterSpan = summer.workingDaysBetweenInclusive(winterStart, winterFinish);
        int summerSpan = summer.workingDaysBetweenInclusive(summerStart, summerFinish);
        assertThat(summerSpan).isGreaterThan(winterSpan);
        assertThat(winterSpan).isEqualTo(8);
    }

    @Test
    @DisplayName("A five-day working week produces a longer programme than the UAE six-day week")
    void fiveDayWeekTakesLonger() {
        WorkingCalendar monToFri = new WorkingCalendar(
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                Set.of());

        CpmResult six = engine.solve(List.of(activity("A", 20)), List.of(), SATURDAY, uae);
        CpmResult five = engine.solve(List.of(activity("A", 20)), List.of(), SATURDAY, monToFri);

        assertThat(five.getProjectFinish()).isAfter(six.getProjectFinish());
        assertThat(five.getTotalWorkingDays()).isEqualTo(20);
        assertThat(six.getTotalWorkingDays()).isEqualTo(20);
    }

    // ---------------------------------------------------------- link types

    @Test
    @DisplayName("Finish-to-start puts the successor on the next working day")
    void finishToStart() {
        List<CpmActivity> activities = List.of(activity("A", 3), activity("B", 2));
        CpmResult result = engine.solve(activities, List.of(CpmLink.fs("A", "B")), SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        assertThat(b.getEarlyStart()).isEqualTo(uae.addWorkingDays(a.getEarlyFinish(), 1));
    }

    @Test
    @DisplayName("Finish-to-start with a lag inserts exactly that many working days of gap")
    void finishToStartWithLag() {
        List<CpmActivity> activities = List.of(activity("A", 3), activity("B", 2));
        CpmLink link = new CpmLink("A", "B", DependencyType.FS, 4, false, null);
        CpmResult result = engine.solve(activities, List.of(link), SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        // Four clear working days between the finish and the start.
        assertThat(uae.workingDaysBetweenInclusive(a.getEarlyFinish(), b.getEarlyStart())).isEqualTo(6);
    }

    @Test
    @DisplayName("Start-to-start with a lag overlaps the two activities")
    void startToStartOverlap() {
        List<CpmActivity> activities = List.of(activity("A", 10), activity("B", 5));
        CpmLink link = new CpmLink("A", "B", DependencyType.SS, 3, false, null);
        CpmResult result = engine.solve(activities, List.of(link), SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        assertThat(b.getEarlyStart()).isEqualTo(uae.addWorkingDays(a.getEarlyStart(), 3));
        assertThat(b.getEarlyStart()).isBefore(a.getEarlyFinish());
    }

    @Test
    @DisplayName("Finish-to-finish holds the successor's finish at or after the predecessor's")
    void finishToFinish() {
        List<CpmActivity> activities = List.of(activity("A", 10), activity("B", 3));
        CpmLink link = new CpmLink("A", "B", DependencyType.FF, 0, false, null);
        CpmResult result = engine.solve(activities, List.of(link), SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        assertThat(b.getEarlyFinish()).isEqualTo(a.getEarlyFinish());
        // B is only three days long, so it starts late rather than early.
        assertThat(b.getEarlyStart()).isEqualTo(uae.startOf(a.getEarlyFinish(), 3));
    }

    @Test
    @DisplayName("Start-to-finish pushes the successor's finish out to the predecessor's start plus lag")
    void startToFinish() {
        // X delays A far enough that the SF link, rather than the project start, is what
        // decides where B lands.
        List<CpmActivity> activities = List.of(activity("X", 15), activity("A", 10), activity("B", 4));
        List<CpmLink> links = List.of(
                CpmLink.fs("X", "A"),
                new CpmLink("A", "B", DependencyType.SF, 2, false, null));

        CpmResult result = engine.solve(activities, links, SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        assertThat(b.getEarlyFinish()).isEqualTo(uae.addWorkingDays(a.getEarlyStart(), 2));
    }

    @Test
    @DisplayName("A finish-driven link is a minimum, so an activity that cannot start earlier finishes later")
    void startToFinishIsAMinimumNotAnEquality() {
        List<CpmActivity> activities = List.of(activity("A", 10), activity("B", 4));
        CpmLink link = new CpmLink("A", "B", DependencyType.SF, 2, false, null);

        CpmResult result = engine.solve(activities, List.of(link), SATURDAY, uae);

        CpmActivity a = find(result, "A");
        CpmActivity b = find(result, "B");
        assertThat(b.getEarlyStart()).isEqualTo(SATURDAY);
        assertThat(b.getEarlyFinish()).isAfterOrEqualTo(uae.addWorkingDays(a.getEarlyStart(), 2));
    }

    // ------------------------------------------------------------- float

    @Test
    @DisplayName("The longer of two parallel chains is critical and the shorter carries float")
    void floatAndCriticality() {
        List<CpmActivity> activities = List.of(
                activity("START", 1), activity("LONG", 10), activity("SHORT", 3), activity("END", 1));
        List<CpmLink> links = List.of(
                CpmLink.fs("START", "LONG"), CpmLink.fs("START", "SHORT"),
                CpmLink.fs("LONG", "END"), CpmLink.fs("SHORT", "END"));

        CpmResult result = engine.solve(activities, links, SATURDAY, uae);

        assertThat(find(result, "LONG").isCritical()).isTrue();
        assertThat(find(result, "LONG").getTotalFloat()).isZero();
        assertThat(find(result, "SHORT").isCritical()).isFalse();
        assertThat(find(result, "SHORT").getTotalFloat()).isEqualTo(7);
        assertThat(find(result, "END").isCritical()).isTrue();
    }

    @Test
    @DisplayName("Critical paths are returned longest first")
    void criticalPathIsTraced() {
        List<CpmActivity> activities = List.of(
                activity("A", 5), activity("B", 5), activity("C", 5), activity("D", 1));
        List<CpmLink> links = List.of(
                CpmLink.fs("A", "B"), CpmLink.fs("B", "C"), CpmLink.fs("A", "D"));

        CpmResult result = engine.solve(activities, links, SATURDAY, uae);

        assertThat(result.getCriticalPaths()).isNotEmpty();
        assertThat(result.getCriticalPaths().get(0)).containsExactly("A", "B", "C");
    }

    // -------------------------------------------------------- constraints

    @Test
    @DisplayName("A constraint date holds an activity back even when its logic allows an earlier start")
    void constraintDelaysAnActivity() {
        CpmActivity blocked = activity("PERMIT_BLOCKED", 5);
        blocked.setConstraintStart(LocalDate.of(2026, 2, 2));
        blocked.setConstraintSource("Awaiting DCD fire approval");

        CpmResult result = engine.solve(List.of(activity("A", 2), blocked),
                List.of(CpmLink.fs("A", "PERMIT_BLOCKED")), SATURDAY, uae);

        assertThat(find(result, "PERMIT_BLOCKED").getEarlyStart()).isEqualTo(LocalDate.of(2026, 2, 2));
    }

    @Test
    @DisplayName("A constraint earlier than the logic allows is ignored, not honoured")
    void constraintCannotPullAnActivityEarlier() {
        CpmActivity b = activity("B", 5);
        b.setConstraintStart(SATURDAY);

        CpmResult result = engine.solve(List.of(activity("A", 10), b),
                List.of(CpmLink.fs("A", "B")), SATURDAY, uae);

        assertThat(find(result, "B").getEarlyStart()).isAfter(find(result, "A").getEarlyFinish());
    }

    @Test
    @DisplayName("A milestone takes no time and lands on its predecessor's next working day")
    void milestoneHasZeroDuration() {
        CpmActivity milestone = activity("HANDOVER", 0);
        milestone.setMilestone(true);

        CpmResult result = engine.solve(List.of(activity("A", 5), milestone),
                List.of(CpmLink.fs("A", "HANDOVER")), SATURDAY, uae);

        CpmActivity m = find(result, "HANDOVER");
        assertThat(m.getEarlyStart()).isEqualTo(m.getEarlyFinish());
        assertThat(m.effectiveDuration()).isZero();
    }

    // ------------------------------------------------------------- faults

    @Test
    @DisplayName("A circular dependency is reported and still produces a schedule")
    void cycleIsReportedNotFatal() {
        List<CpmActivity> activities = List.of(activity("A", 3), activity("B", 3));
        List<CpmLink> links = List.of(CpmLink.fs("A", "B"), CpmLink.fs("B", "A"));

        CpmResult result = engine.solve(activities, links, SATURDAY, uae);

        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Circular dependency"));
        assertThat(result.getProjectFinish()).isNotNull();
    }

    @Test
    @DisplayName("A dependency naming an activity that is not in the network is dropped with a warning")
    void danglingDependencyIsReported() {
        CpmResult result = engine.solve(List.of(activity("A", 3)),
                List.of(CpmLink.fs("GHOST", "A")), SATURDAY, uae);

        assertThat(result.getWarnings()).anyMatch(w -> w.contains("GHOST"));
        assertThat(find(result, "A").getEarlyStart()).isEqualTo(SATURDAY);
    }

    @Test
    @DisplayName("An empty programme solves to a zero-length result rather than throwing")
    void emptyNetwork() {
        CpmResult result = engine.solve(new ArrayList<>(), List.of(), SATURDAY, uae);
        assertThat(result.getProjectStart()).isEqualTo(SATURDAY);
        assertThat(result.getActivities()).isEmpty();
    }

    @Test
    @DisplayName("A chain of 300 activities solves without blowing up")
    void largeNetworkPerformance() {
        List<CpmActivity> activities = new ArrayList<>();
        List<CpmLink> links = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            activities.add(activity("A" + i, 1));
            if (i > 0) links.add(CpmLink.fs("A" + (i - 1), "A" + i));
        }

        long started = System.currentTimeMillis();
        CpmResult result = engine.solve(activities, links, SATURDAY, uae);
        long elapsed = System.currentTimeMillis() - started;

        assertThat(result.getTotalWorkingDays()).isEqualTo(300);
        assertThat(result.criticalActivities()).hasSize(300);
        assertThat(elapsed).isLessThan(10_000);
    }

    private CpmActivity find(CpmResult result, String code) {
        return result.getActivities().stream()
                .filter(a -> code.equals(a.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No activity " + code));
    }
}
