package com.fitouts.auth.domain;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    BUSINESS_OWNER,
    PROJECT_MANAGER,
    DESIGNER,
    QAS,
    QS,
    SENIOR_QS,
    FINANCE,
    SUBCONTRACTOR,
    CLIENT,
    SALES,
    EMPLOYEE,
    SITE_ENGINEER;

    public String displayLabel() {
        return switch (this) {
            case SUPER_ADMIN -> "Super Admin";
            case ADMIN -> "Admin";
            case BUSINESS_OWNER -> "Project Director";
            case PROJECT_MANAGER -> "Project Manager";
            case DESIGNER -> "Designer";
            case QAS -> "QAS";
            case QS -> "Quantity Surveyor";
            case SENIOR_QS -> "Senior QS";
            case FINANCE -> "Finance / Accounts";
            case SUBCONTRACTOR -> "Subcontractor";
            case CLIENT -> "Client";
            case SALES -> "Sales";
            case EMPLOYEE -> "Employee";
            case SITE_ENGINEER -> "Site Engineer";
        };
    }
}
