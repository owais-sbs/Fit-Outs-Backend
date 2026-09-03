package com.fitouts.project.domain;

public enum ProjectTeamRole {
    QS_SENIOR_QS,
    PROJECT_MANAGER,
    FINANCE,
    CLIENT,
    SUBCONTRACTOR;

    public String displayLabel() {
        return switch (this) {
            case QS_SENIOR_QS -> "QS / Senior QS";
            case PROJECT_MANAGER -> "Project Manager";
            case FINANCE -> "Finance";
            case CLIENT -> "Client";
            case SUBCONTRACTOR -> "Subcontractor";
        };
    }
}
