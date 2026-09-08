package com.fitouts.schedule.engine;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * The six parameters that drive template scaling, plus the scope toggles that decide which
 * activities exist at all.
 *
 * <p>Base values are the template's reference project. Actual values are this project. The
 * ratio between them is what the parametric scaler uses.
 */
@Getter
@Setter
public class ScheduleParameters {

    private LocalDate startDate;

    /** Gross floor area. The primary driver for most fit-out trades. */
    private Double areaSqft;
    private Double baseAreaSqft;

    private Integer roomCount;
    private Integer baseRoomCount;

    private Integer floorCount;
    private Integer baseFloorCount;

    /** Standard, premium, ultra-luxury. Higher specification means slower, more careful work. */
    private String finishLevel;
    private String baseFinishLevel;

    /** Simultaneous work fronts. More crews compress quantity-driven work, not cure times. */
    private Integer crewCount;
    private Integer baseCrewCount;

    /** Occupied buildings restrict hours and access. */
    private Boolean occupiedBuilding;

    private Map<String, Boolean> scopeToggles = new LinkedHashMap<>();

    /** Measured quantities by scaling driver code, when a BOQ exists. */
    private Map<String, Double> quantities = new LinkedHashMap<>();

    private boolean fastTrackAcknowledged;

    public boolean toggleOn(String code) {
        if (code == null || code.isBlank()) return true;
        Boolean value = scopeToggles.get(code);
        return value == null || value;
    }

    /** Finish level as a duration multiplier. Premium work is slower per unit. */
    public double finishFactor() {
        return finishFactorOf(finishLevel) / finishFactorOf(baseFinishLevel);
    }

    private static double finishFactorOf(String level) {
        if (level == null) return 1.0;
        return switch (level.trim().toUpperCase().replace(' ', '_')) {
            case "BASIC", "SHELL" -> 0.85;
            case "STANDARD" -> 1.0;
            case "PREMIUM", "HIGH" -> 1.15;
            case "ULTRA_LUXURY", "ULTRA", "LUXURY", "BESPOKE" -> 1.35;
            default -> 1.0;
        };
    }
}
