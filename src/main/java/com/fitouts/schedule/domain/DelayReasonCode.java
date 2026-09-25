package com.fitouts.schedule.domain;

public enum DelayReasonCode {
    WEATHER("Adverse weather"),
    MATERIAL_DELAY("Material / delivery delay"),
    CLIENT_CHANGE("Client instruction / scope change"),
    ACCESS_CONSTRAINT("Site access / permit constraint"),
    LABOUR_SHORTAGE("Labour shortage"),
    DESIGN_HOLD("Design / drawing hold"),
    OTHER("Custom");

    private final String label;

    DelayReasonCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
