package com.fitouts.boq.application;

import com.fitouts.shared.enums.QtoLineType;

public final class BoqJctMapper {

    private BoqJctMapper() {}

    public static String categoryCode(QtoLineType type) {
        return switch (type) {
            case FLOOR_AREA, TILE_QTY, MARBLE, GRANITE -> "E";
            case CEILING_AREA, FALSE_CEILING -> "F";
            case PAINT_AREA -> "G";
            case PLUMBING_FIXTURE -> "H";
            case LIGHTING_FIXTURE -> "I";
            case DOOR_COUNT, WINDOW_COUNT, SKIRTING_LENGTH -> "K";
            case WALL_AREA -> "D";
            default -> "OTHER";
        };
    }

    public static String categoryName(String code) {
        return switch (code) {
            case "A" -> "Approvals & Drawings";
            case "B" -> "Preliminaries";
            case "C" -> "Demolition Work";
            case "D" -> "Building Works";
            case "E" -> "Floor & Wall Cladding";
            case "F" -> "Ceiling Works";
            case "G" -> "Painting Works";
            case "H" -> "Plumbing Works";
            case "I" -> "Electrical Works";
            case "J" -> "Exhaust & A/C Works";
            case "K" -> "Joinery Works";
            case "L" -> "Counter Top & Marble Works";
            case "M" -> "Aluminum and Glass Works";
            case "N" -> "Purchases";
            case "OPT" -> "Optional Works";
            default -> "Other Charges";
        };
    }
}
