package com.fitouts.schedule.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fitouts.schedule.domain.ProductivityNorm;
import com.fitouts.schedule.domain.TemplateActivity;

/**
 * Turns a template's base duration into a duration for this project.
 *
 * <p>Three methods, in descending order of confidence:
 * <ul>
 *   <li><b>Quantity</b> — a measured quantity divided by crew output. Only used when the BOQ
 *       actually carries the driver quantity and a productivity norm exists.</li>
 *   <li><b>Parametric</b> — the base duration scaled by the size ratio raised to 0.85, because
 *       doubling the area does not double the programme. Capped at ±60% so a mis-keyed
 *       parameter cannot produce an absurd duration.</li>
 *   <li><b>Fixed</b> — never scales. Cure times, statutory notice periods, tests.</li>
 * </ul>
 */
@Component
public class DurationScaler {

    /** Sub-linear exponent: crews overlap, so effort grows faster than elapsed time. */
    private static final double PARAMETRIC_EXPONENT = 0.85;
    private static final double MAX_FACTOR = 1.6;
    private static final double MIN_FACTOR = 0.4;

    public Scaled scale(TemplateActivity activity, ScheduleParameters params, List<ProductivityNorm> norms) {
        int base = Math.max(1, activity.getBaseDurationDays());
        String method = activity.getScalingMethod() == null ? "PARAMETRIC" : activity.getScalingMethod();

        if (activity.isMilestone()) {
            return new Scaled(0, "FIXED", "Milestone; zero duration");
        }
        if (activity.isLockedDuration() || "FIXED".equals(method)) {
            return new Scaled(base, "FIXED",
                    activity.getConstraintNote() != null
                            ? "Fixed: " + activity.getConstraintNote()
                            : "Fixed duration; not scaled");
        }

        if ("QUANTITY".equals(method)) {
            Scaled fromQuantity = scaleByQuantity(activity, params, norms, base);
            if (fromQuantity != null) return fromQuantity;
            // Fall through: no measured quantity yet, so parametric is the honest answer.
        }

        double ratio = sizeRatio(activity, params);
        double factor = Math.pow(ratio, PARAMETRIC_EXPONENT) * params.finishFactor();
        factor = clamp(factor);

        int scaled = (int) Math.max(1, Math.round(base * factor));
        String note = String.format("Parametric: %d base days x %.2f (size %.2f, finish %.2f)",
                base, factor, ratio, params.finishFactor());
        return new Scaled(scaled, "PARAMETRIC", note);
    }

    private Scaled scaleByQuantity(TemplateActivity activity, ScheduleParameters params,
                                   List<ProductivityNorm> norms, int base) {
        String driver = activity.getScalingDriver();
        if (driver == null) return null;

        Double quantity = params.getQuantities().get(driver);
        if (quantity == null || quantity <= 0) return null;

        ProductivityNorm norm = matchNorm(activity, norms);
        if (norm == null) return null;

        Double output = norm.midOutput();
        if (output == null || output <= 0) return null;

        int crews = params.getCrewCount() == null || params.getCrewCount() < 1 ? 1 : params.getCrewCount();
        double days = quantity / (output * crews);
        // Never fall below the template's floor: mobilisation and inspection do not vanish
        // because the quantity is small.
        int floor = Math.max(1, (int) Math.ceil(base * 0.5));
        int scaled = Math.max(floor, (int) Math.ceil(days * params.finishFactor()));

        String note = String.format("Quantity: %.0f %s / (%.1f per crew-day x %d crews) = %d days (floor %d)",
                quantity, norm.getUnit() == null ? "units" : norm.getUnit(), output, crews, scaled, floor);
        return new Scaled(scaled, "QUANTITY", note);
    }

    private ProductivityNorm matchNorm(TemplateActivity activity, List<ProductivityNorm> norms) {
        if (norms == null || norms.isEmpty()) return null;
        String ref = activity.getCrewOutputRef();
        if (ref != null) {
            for (ProductivityNorm n : norms) {
                if (ref.equalsIgnoreCase(n.getWorkItem())) return n;
            }
        }
        String haystack = (activity.getName() == null ? "" : activity.getName()).toLowerCase();
        for (ProductivityNorm n : norms) {
            if (n.getMatchKeywords() == null) continue;
            for (String keyword : n.getMatchKeywords().split(",")) {
                String k = keyword.trim().toLowerCase();
                if (!k.isEmpty() && haystack.contains(k)) return n;
            }
        }
        return null;
    }

    /** How much bigger this project is than the template's reference project. */
    private double sizeRatio(TemplateActivity activity, ScheduleParameters params) {
        String driver = activity.getScalingDriver() == null ? "AREA" : activity.getScalingDriver().toUpperCase();
        Double actual;
        Double base;

        switch (driver) {
            case "ROOMS", "ROOM_COUNT" -> {
                actual = toDouble(params.getRoomCount());
                base = toDouble(params.getBaseRoomCount());
            }
            case "FLOORS", "FLOOR_COUNT" -> {
                actual = toDouble(params.getFloorCount());
                base = toDouble(params.getBaseFloorCount());
            }
            case "BEDROOMS", "BEDROOM_COUNT" -> {
                actual = toDouble(params.getBedrooms());
                base = toDouble(params.getBaseBedrooms());
            }
            case "BATHROOMS", "BATHROOM_COUNT" -> {
                actual = toDouble(params.getBathrooms());
                base = toDouble(params.getBaseBathrooms());
            }
            case "KITCHENS", "KITCHEN_COUNT" -> {
                actual = toDouble(params.getKitchens());
                base = toDouble(params.getBaseKitchens());
            }
            default -> {
                actual = params.getAreaSqft();
                base = params.getBaseAreaSqft();
            }
        }

        if (actual == null || base == null || base <= 0 || actual <= 0) return 1.0;
        return actual / base;
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private static double clamp(double factor) {
        if (Double.isNaN(factor) || Double.isInfinite(factor) || factor <= 0) return 1.0;
        return Math.min(MAX_FACTOR, Math.max(MIN_FACTOR, factor));
    }

    /** Scaled duration plus the reasoning, so the preview screen can explain itself. */
    public record Scaled(int durationDays, String methodUsed, String explanation) { }
}
