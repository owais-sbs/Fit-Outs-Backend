package com.fitouts.project.domain;

public enum ProjectTeamRole {
    QS_SENIOR_QS,
    PROJECT_MANAGER,
    SITE_ENGINEER,
    FINANCE,
    CLIENT,
    SUBCONTRACTOR;

    public String displayLabel() {
        return switch (this) {
            case QS_SENIOR_QS -> "QS / Senior QS";
            case PROJECT_MANAGER -> "Project Manager";
            case SITE_ENGINEER -> "Site Engineer";
            case FINANCE -> "Finance";
            case CLIENT -> "Client";
            case SUBCONTRACTOR -> "Subcontractor";
        };
    }
}
