package com.fitouts.shared.context;

import java.util.UUID;

public class CompanyContext {

    private static final ThreadLocal<UUID> currentCompany = new ThreadLocal<>();

    public static void set(UUID companyId) {
        currentCompany.set(companyId);
    }

    public static UUID get() {
        return currentCompany.get();
    }

    public static void clear() {
        currentCompany.remove();
    }
}
