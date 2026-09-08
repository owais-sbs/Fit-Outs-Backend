package com.fitouts.approval.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Finds the VetroBuild seed JSON. The file lives in the repo's {@code docs} folder rather than
 * under {@code resources}, so a spreadsheet-sized companion never ships inside the jar. The
 * backend may be started from the repo root or from the module directory, so both are tried,
 * and a classpath copy is used as the deployment fallback.
 */
@Component
public class SeedFileLocator {

    private static final List<String> CANDIDATE_PATHS = List.of(
            "docs/vetrobuild_erp_seed_v1.json",
            "../docs/vetrobuild_erp_seed_v1.json",
            "../../docs/vetrobuild_erp_seed_v1.json");

    private static final String CLASSPATH_FALLBACK = "seed/vetrobuild_erp_seed_v1.json";

    private final String configuredPath;

    public SeedFileLocator(@Value("${fitouts.seed.path:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    /** Returns the resolved location for display, or null when nothing is found. */
    public String describeLocation() {
        Path found = locateOnDisk();
        if (found != null) return found.toAbsolutePath().toString();
        return new ClassPathResource(CLASSPATH_FALLBACK).exists() ? "classpath:" + CLASSPATH_FALLBACK : null;
    }

    public boolean exists() {
        return describeLocation() != null;
    }

    /** Opens the seed file. The caller closes the stream. */
    public InputStream open() throws IOException {
        Path found = locateOnDisk();
        if (found != null) {
            return Files.newInputStream(found);
        }
        ClassPathResource fallback = new ClassPathResource(CLASSPATH_FALLBACK);
        if (fallback.exists()) {
            return fallback.getInputStream();
        }
        throw new IOException("Seed file not found. Looked in " + String.join(", ", CANDIDATE_PATHS)
                + " and classpath:" + CLASSPATH_FALLBACK);
    }

    private Path locateOnDisk() {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path explicit = Paths.get(configuredPath);
            if (Files.isReadable(explicit)) return explicit;
        }
        for (String candidate : CANDIDATE_PATHS) {
            Path path = Paths.get(candidate);
            if (Files.isReadable(path)) return path;
        }
        return null;
    }
}
