package com.fitouts.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fitouts.approval.application.ApprovalSeedImportService;
import com.fitouts.schedule.application.TemplatePlan;
import com.fitouts.schedule.application.TemplatePlanner;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.ScheduleTemplateRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.ScheduleParameters;
import com.fitouts.schedule.engine.WorkingCalendar;

/**
 * The behaviour a PM depends on when they change something on the apply wizard: scaling the
 * project up, dropping a scope, and being stopped from publishing a programme that is already
 * unbuildable. Run against the real seed templates rather than a synthetic network, because
 * the point is that these rules survive contact with the actual 133-activity data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplatePlanAcceptanceTest {

    private static final String RENOVATION = "TPL-RENO-90";
    private static final String FAST_TRACK = "TPL-RENO-60";

    @Autowired private ApprovalSeedImportService importService;
    @Autowired private TemplatePlanner planner;
    @Autowired private ScheduleTemplateRepository templateRepository;

    private final WorkingCalendar calendar = WorkingCalendar.uaeDefault();

    @BeforeEach
    void seedOnce() {
        if (templateRepository.findByCodeAndCompanyIdIsNull(RENOVATION).isEmpty()) {
            importService.importFromConfiguredFile();
        }
    }

    /** A start far enough out that no long-lead order-by date has already passed. */
    private ScheduleParameters defaults() {
        ScheduleParameters params = new ScheduleParameters();
        params.setStartDate(LocalDate.now().plusMonths(9));
        params.setAreaSqft(6000.0);
        params.setBaseAreaSqft(6000.0);
        return params;
    }

    private ScheduleTemplate template(String code) {
        return templateRepository.findByCodeAndCompanyIdIsNull(code).orElseThrow();
    }

    @Test
    void growingTheVillaStretchesQuantityWorkAndLeavesCureTimesAlone() {
        ScheduleTemplate template = template(RENOVATION);

        TemplatePlan asBuilt = planner.plan(template, defaults(), calendar);

        ScheduleParameters bigger = defaults();
        bigger.setAreaSqft(9000.0);
        TemplatePlan enlarged = planner.plan(template, bigger, calendar);

        assertThat(enlarged.getResult().getTotalWorkingDays())
                .as("half again the floor area cannot leave the programme length unchanged")
                .isGreaterThan(asBuilt.getResult().getTotalWorkingDays());

        Map<String, CpmActivity> before = index(asBuilt);
        Map<String, CpmActivity> after = index(enlarged);

        boolean anyQuantityGrew = false;
        for (Map.Entry<String, CpmActivity> entry : after.entrySet()) {
            CpmActivity now = entry.getValue();
            CpmActivity was = before.get(entry.getKey());
            if (was == null) continue;

            if (now.isLockedDuration()) {
                assertThat(now.getDurationWorkingDays())
                        .as("%s is a fixed hold; a bigger villa does not make concrete cure faster or slower",
                                now.getCode())
                        .isEqualTo(was.getDurationWorkingDays());
            } else if (now.getDurationWorkingDays() > was.getDurationWorkingDays()) {
                anyQuantityGrew = true;
            }
        }
        assertThat(anyQuantityGrew)
                .as("at least one scalable activity has to absorb the extra area")
                .isTrue();
    }

    @Test
    void lockedLagsSurviveScaling() {
        ScheduleParameters bigger = defaults();
        bigger.setAreaSqft(12000.0);

        TemplatePlan asBuilt = planner.plan(template(RENOVATION), defaults(), calendar);
        TemplatePlan enlarged = planner.plan(template(RENOVATION), bigger, calendar);

        Map<String, Integer> lagsBefore = lockedLags(asBuilt);
        Map<String, Integer> lagsAfter = lockedLags(enlarged);

        assertThat(lagsAfter)
                .as("a locked lag is a physical hold; doubling the project cannot shorten it")
                .containsExactlyInAnyOrderEntriesOf(lagsBefore);
    }

    @Test
    void turningLandscapeOffRemovesThoseActivitiesAndResolvesTheNetwork() {
        ScheduleTemplate template = template(RENOVATION);

        TemplatePlan withLandscape = planner.plan(template, defaults(), calendar);
        long landscapeActivities = withLandscape.getActivities().stream()
                .filter(a -> "PKG-LAN".equals(a.getTradePackageCode()))
                .count();
        assertThat(landscapeActivities)
                .as("the renovation template should carry landscape work to begin with")
                .isPositive();

        ScheduleParameters noLandscape = defaults();
        noLandscape.getScopeToggles().put("LANDSCAPE", false);
        TemplatePlan without = planner.plan(template, noLandscape, calendar);

        assertThat(without.getActivities())
                .as("a project with no garden should not carry garden activities")
                .noneMatch(a -> "PKG-LAN".equals(a.getTradePackageCode()));
        assertThat(without.getExcludedByToggleCount()).isEqualTo((int) landscapeActivities);

        assertThat(without.getResult().getTotalWorkingDays())
                .as("dropping scope must produce a re-solved programme, never a broken one")
                .isPositive()
                .isLessThanOrEqualTo(withLandscape.getResult().getTotalWorkingDays());
        assertThat(without.getResult().getWarnings())
                .as("removing an activity must not leave a dangling link behind")
                .noneMatch(w -> w.toLowerCase().contains("unknown activity"));
    }

    @Test
    void aStartDateThatHasAlreadyMissedAnOrderDeadlineIsBlockedAndTheItemIsNamed() {
        ScheduleParameters startingTomorrow = defaults();
        startingTomorrow.setStartDate(LocalDate.now().plusDays(1));

        TemplatePlan plan = planner.plan(template(RENOVATION), startingTomorrow, calendar);

        assertThat(plan.getBlockers())
                .as("a 55-day aluminium lead cannot be met by a programme starting tomorrow")
                .isNotEmpty();

        List<TemplatePlan.OrderByLine> overdue = plan.getOrderByLines().stream()
                .filter(TemplatePlan.OrderByLine::isOverdue)
                .toList();
        assertThat(overdue).isNotEmpty();

        String firstOverdueItem = overdue.get(0).getItemName();
        assertThat(plan.getBlockers())
                .as("the blocker has to name the item, or nobody knows what to chase")
                .anySatisfy(b -> assertThat(b).contains(firstOverdueItem));
    }

    @Test
    void theFastTrackTemplateCarriesConditionsThatHaveToBeAcknowledged() {
        ScheduleTemplate a2 = template(FAST_TRACK);

        assertThat(a2.isFastTrack())
                .as("a 60-day programme is not publishable as an ordinary one")
                .isTrue();
        assertThat(a2.getFastTrackConditionsJson())
                .as("the conditions the client is accepting must be recorded against the template")
                .isNotNull()
                .contains("only");
    }

    private Map<String, CpmActivity> index(TemplatePlan plan) {
        return plan.getActivities().stream()
                .collect(java.util.stream.Collectors.toMap(CpmActivity::getCode, a -> a));
    }

    private Map<String, Integer> lockedLags(TemplatePlan plan) {
        return plan.getLinks().stream()
                .filter(CpmLink::isLocked)
                .collect(java.util.stream.Collectors.toMap(
                        l -> l.getPredecessorCode() + ">" + l.getSuccessorCode(),
                        CpmLink::getLagWorkingDays,
                        (a, b) -> a));
    }
}
