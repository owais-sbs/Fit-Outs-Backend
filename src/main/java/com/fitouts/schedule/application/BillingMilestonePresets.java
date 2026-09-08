package com.fitouts.schedule.application;

import java.util.List;
import java.util.Locale;

import com.fitouts.schedule.engine.CpmActivity;

/**
 * Client payment gates by template, mirroring the frontend presets so the apply cascade can
 * create the same milestones the PM would otherwise pick by hand.
 *
 * <p>Percentages come from the spec's Part B5 (90-day) and Part B6 (60-day) schedules.
 * Retention lines carry no activity link: they fall due after the defect liability period,
 * not on a programme gate.
 */
public final class BillingMilestonePresets {

    private BillingMilestonePresets() { }

    public record Milestone(String name, int paymentPercent, Integer percentCompleteRequired,
                            List<String> activityKeywords, boolean retention) { }

    private static final List<Milestone> TEMPLATE_A = List.of(
            new Milestone("Mobilisation — contract award and kick-off", 10, null,
                    List.of("contract award", "kick-off", "r010"), false),
            new Milestone("Pre-construction — community NOC and mobilisation", 15, 15,
                    List.of("community noc", "site mobilisation", "r060", "r070"), false),
            new Milestone("Structure and wet works — tiling complete", 25, 50,
                    List.of("tiling", "wet areas", "r410"), false),
            new Milestone("Envelope watertight — aluminium and gypsum", 15, 70,
                    List.of("aluminium", "glazing", "gypsum", "r440", "r450"), false),
            new Milestone("Fit-out installation — joinery and second fix", 20, 90,
                    List.of("joinery", "second fix", "r490", "r540"), false),
            new Milestone("Practical completion — handover", 10, 100,
                    List.of("handover", "dlp", "r600"), false),
            new Milestone("Retention release — defect liability period", 5, 100,
                    List.of(), true));

    private static final List<Milestone> TEMPLATE_A2 = List.of(
            new Milestone("Mobilisation — contract and specification pack", 15, null,
                    List.of("contract", "specification", "f010"), false),
            new Milestone("Fast-track permits and site mobilisation", 15, 15,
                    List.of("permit", "mobilisation", "f020", "f100"), false),
            new Milestone("Demolition and MEP first fix — Zone A", 20, 40,
                    List.of("zone a", "demolition", "f110", "f130"), false),
            new Milestone("Aluminium installation and joinery delivery", 25, 70,
                    List.of("aluminium", "joinery", "f220", "f270"), false),
            new Milestone("Commissioning, snagging and handover", 15, 100,
                    List.of("testing", "handover", "f330", "f370"), false),
            new Milestone("Retention release — defect liability period", 10, 100,
                    List.of(), true));

    /** Generic split for templates without a published payment schedule. */
    private static final List<Milestone> GENERIC = List.of(
            new Milestone("Mobilisation", 15, null, List.of("mobilis", "kick-off", "award"), false),
            new Milestone("First fix complete", 25, 35, List.of("first fix", "rough-in"), false),
            new Milestone("Second fix complete", 30, 70, List.of("second fix", "joinery"), false),
            new Milestone("Practical completion", 25, 100, List.of("handover", "completion", "snag"), false),
            new Milestone("Retention release", 5, 100, List.of(), true));

    public static List<Milestone> forTemplate(String templateCode) {
        if (templateCode == null) return GENERIC;
        return switch (templateCode.trim().toUpperCase(Locale.ROOT)) {
            case "A", "TEMPLATE-A", "TEMPLATE_A" -> TEMPLATE_A;
            case "A2", "TEMPLATE-A2", "TEMPLATE_A2" -> TEMPLATE_A2;
            default -> GENERIC;
        };
    }

    /** First activity whose code or name contains one of the keywords. */
    public static CpmActivity match(List<CpmActivity> activities, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return null;
        for (String keyword : keywords) {
            String needle = keyword.toLowerCase(Locale.ROOT);
            for (CpmActivity a : activities) {
                String code = a.getCode() == null ? "" : a.getCode().toLowerCase(Locale.ROOT);
                String name = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
                if (code.equals(needle) || name.contains(needle)) return a;
            }
        }
        return null;
    }
}
