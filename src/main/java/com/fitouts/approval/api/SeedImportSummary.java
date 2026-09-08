package com.fitouts.approval.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * What an import actually did. Counts are per section, and warnings carry anything the
 * parser had to guess at so it stays visible instead of disappearing into the database.
 */
@Getter
@Setter
public class SeedImportSummary {

    private String source;
    private String seedVersion;
    private Map<String, Integer> inserted = new LinkedHashMap<>();
    private Map<String, Integer> updated = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    public void countInserted(String section, int count) {
        inserted.merge(section, count, Integer::sum);
    }

    public void countUpdated(String section, int count) {
        updated.merge(section, count, Integer::sum);
    }

    public void warn(String message) {
        warnings.add(message);
    }

    public int totalInserted() {
        return inserted.values().stream().mapToInt(Integer::intValue).sum();
    }
}
