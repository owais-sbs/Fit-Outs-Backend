package com.fitouts.schedule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fitouts.schedule.domain.ProductivityNorm;
import com.fitouts.schedule.domain.TemplateActivity;

/**
 * The scaler decides how long a template activity takes on this particular project. Getting it
 * wrong in either direction is expensive, so the rules are pinned down here.
 */
class DurationScalerTest {

    private final DurationScaler scaler = new DurationScaler();

    private TemplateActivity activity(String name, int baseDays, String method) {
        TemplateActivity ta = new TemplateActivity();
        ta.setActivityCode("A010");
        ta.setName(name);
        ta.setBaseDurationDays(baseDays);
        ta.setScalingMethod(method);
        return ta;
    }

    /** Template reference project: 6000 sqft, standard finish, one crew. */
    private ScheduleParameters baseParams() {
        ScheduleParameters params = new ScheduleParameters();
        params.setBaseAreaSqft(6000.0);
        params.setAreaSqft(6000.0);
        params.setBaseFinishLevel("STANDARD");
        params.setFinishLevel("STANDARD");
        params.setBaseCrewCount(1);
        params.setCrewCount(1);
        return params;
    }

    @Test
    @DisplayName("At the template's own size, a parametric activity keeps its base duration")
    void noChangeAtReferenceSize() {
        var scaled = scaler.scale(activity("Blockwork", 10, "PARAMETRIC"), baseParams(), List.of());
        assertThat(scaled.durationDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("Growing 6000 to 9000 sqft lengthens the activity, but by less than the size ratio")
    void parametricScalingIsSubLinear() {
        ScheduleParameters params = baseParams();
        params.setAreaSqft(9000.0);

        var scaled = scaler.scale(activity("Blockwork", 20, "PARAMETRIC"), params, List.of());

        // 1.5x the area, so a linear scale would give 30 days. The 0.85 exponent gives less,
        // because crews overlap as the site gets bigger.
        assertThat(scaled.durationDays()).isGreaterThan(20).isLessThan(30);
    }

    @Test
    @DisplayName("A fixed activity ignores every parameter, because cure time is physics")
    void fixedNeverScales() {
        ScheduleParameters params = baseParams();
        params.setAreaSqft(60000.0);
        params.setCrewCount(20);

        var scaled = scaler.scale(activity("Screed curing", 7, "FIXED"), params, List.of());

        assertThat(scaled.durationDays()).isEqualTo(7);
        assertThat(scaled.methodUsed()).isEqualTo("FIXED");
    }

    @Test
    @DisplayName("A locked duration is treated as fixed even when the method says otherwise")
    void lockedDurationOverridesTheMethod() {
        TemplateActivity ta = activity("Waterproofing flood test", 3, "PARAMETRIC");
        ta.setLockedDuration(true);
        ta.setConstraintNote("48-hour flood test cannot be shortened");

        ScheduleParameters params = baseParams();
        params.setAreaSqft(18000.0);

        var scaled = scaler.scale(ta, params, List.of());

        assertThat(scaled.durationDays()).isEqualTo(3);
        assertThat(scaled.explanation()).contains("flood test");
    }

    @Test
    @DisplayName("A milestone has no duration whatever the parameters say")
    void milestoneIsZero() {
        TemplateActivity ta = activity("Practical completion", 1, "PARAMETRIC");
        ta.setMilestone(true);

        assertThat(scaler.scale(ta, baseParams(), List.of()).durationDays()).isZero();
    }

    @Test
    @DisplayName("A measured quantity and a crew output produce a duration from first principles")
    void quantityDrivenScaling() {
        TemplateActivity ta = activity("Floor tiling", 10, "QUANTITY");
        ta.setScalingDriver("TILING_SQM");
        ta.setCrewOutputRef("Floor tiling");

        ProductivityNorm norm = new ProductivityNorm();
        norm.setWorkItem("Floor tiling");
        norm.setUnit("sqm");
        norm.setOutputPerCrewPerDayMin(BigDecimal.valueOf(30));
        norm.setOutputPerCrewPerDayMax(BigDecimal.valueOf(40));

        ScheduleParameters params = baseParams();
        params.getQuantities().put("TILING_SQM", 700.0);
        params.setCrewCount(2);

        var scaled = scaler.scale(ta, params, List.of(norm));

        // 700 sqm at 35 sqm per crew-day with two crews is ten days.
        assertThat(scaled.durationDays()).isEqualTo(10);
        assertThat(scaled.methodUsed()).isEqualTo("QUANTITY");
        assertThat(scaled.explanation()).contains("700");
    }

    @Test
    @DisplayName("A tiny quantity still cannot drop below half the template duration")
    void quantityScalingHasAFloor() {
        TemplateActivity ta = activity("Floor tiling", 10, "QUANTITY");
        ta.setScalingDriver("TILING_SQM");
        ta.setCrewOutputRef("Floor tiling");

        ProductivityNorm norm = new ProductivityNorm();
        norm.setWorkItem("Floor tiling");
        norm.setOutputPerCrewPerDayMin(BigDecimal.valueOf(35));
        norm.setOutputPerCrewPerDayMax(BigDecimal.valueOf(35));

        ScheduleParameters params = baseParams();
        params.getQuantities().put("TILING_SQM", 10.0);

        // Mobilisation, setting out and inspection do not vanish because the room is small.
        assertThat(scaler.scale(ta, params, List.of(norm)).durationDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("Without a measured quantity, a quantity-driven activity falls back to parametric")
    void quantityFallsBackWhenTheBoqIsEmpty() {
        TemplateActivity ta = activity("Floor tiling", 10, "QUANTITY");
        ta.setScalingDriver("TILING_SQM");

        var scaled = scaler.scale(ta, baseParams(), List.of());

        assertThat(scaled.methodUsed()).isEqualTo("PARAMETRIC");
        assertThat(scaled.durationDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("An ultra-luxury finish takes longer than the same work at standard finish")
    void finishLevelSlowsTheWork() {
        ScheduleParameters standard = baseParams();
        ScheduleParameters luxury = baseParams();
        luxury.setFinishLevel("ULTRA_LUXURY");

        int standardDays = scaler.scale(activity("Painting", 20, "PARAMETRIC"), standard, List.of()).durationDays();
        int luxuryDays = scaler.scale(activity("Painting", 20, "PARAMETRIC"), luxury, List.of()).durationDays();

        assertThat(luxuryDays).isGreaterThan(standardDays);
    }

    @Test
    @DisplayName("An absurd parameter is clamped rather than producing a nonsense programme")
    void scalingIsCapped() {
        ScheduleParameters params = baseParams();
        params.setAreaSqft(6_000_000.0);

        // Capped at 1.6x: a mis-keyed area should not silently produce a ten-year programme.
        assertThat(scaler.scale(activity("Blockwork", 10, "PARAMETRIC"), params, List.of()).durationDays())
                .isEqualTo(16);
    }

    @Test
    @DisplayName("A missing base size leaves the duration untouched instead of dividing by zero")
    void missingBaseSizeIsSafe() {
        ScheduleParameters params = new ScheduleParameters();
        params.setAreaSqft(9000.0);

        assertThat(scaler.scale(activity("Blockwork", 12, "PARAMETRIC"), params, List.of()).durationDays())
                .isEqualTo(12);
    }
}
