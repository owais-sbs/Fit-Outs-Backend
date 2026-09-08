package com.fitouts.approval.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitouts.approval.api.SeedImportSummary;

/**
 * Hook for the scheduling module to claim its sections of the seed file.
 *
 * <p>The companion file carries both the approval catalogue and the schedule templates,
 * long-lead matrix, locked constraints and productivity norms. Rather than have the approval
 * importer know about scheduling, the scheduling module implements this and is resolved lazily.
 */
public interface ScheduleTemplateSeedImporter {

    /**
     * Imports schedule_templates, long_lead_matrix, locked_constraints and productivity_norms.
     *
     * @param root    the parsed seed document
     * @param summary counts and warnings to append to
     */
    void importTemplates(JsonNode root, SeedImportSummary summary);
}
