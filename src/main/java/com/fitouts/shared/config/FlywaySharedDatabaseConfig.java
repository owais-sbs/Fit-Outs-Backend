package com.fitouts.shared.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywaySharedDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywaySharedDatabaseConfig.class);

    /**
     * After merging main, h-dev approvals scripts that collided on V73–V75 / V81–V83
     * were moved to V600–V605 (Humaid range). Shared RDS may still record the old numbers.
     * Remap those history rows so main's subcontractor V73–V80 can occupy that range.
     */
    private static final List<VersionRemap> HDEV_COLLISION_REMAPS = List.of(
            new VersionRemap("73", "V73__permit_trigger_types_backfill.sql",
                    "600", "V600__permit_trigger_types_backfill.sql"),
            new VersionRemap("74", "V74__resolver_emirate_wide_and_project_classifiers.sql",
                    "601", "V601__resolver_emirate_wide_and_project_classifiers.sql"),
            new VersionRemap("75", "V75__permit_authority_resolution.sql",
                    "602", "V602__permit_authority_resolution.sql"),
            new VersionRemap("81", "V81__permit_authority_resolution_backfill.sql",
                    "603", "V603__permit_authority_resolution_backfill.sql"),
            new VersionRemap("82", "V82__approval_case_authority_manually_set.sql",
                    "604", "V604__approval_case_authority_manually_set.sql"),
            new VersionRemap("83", "V83__reopen_withdrawn_approval_cases.sql",
                    "605", "V605__reopen_withdrawn_approval_cases.sql")
    );

    /**
     * Shared RDS has migrations from other branches. Community Flyway validate fails when those
     * files are missing. Repair realigns checksums when an applied migration file was rewritten
     * locally (e.g. after main's V55–V59 were moved to V64–V68 on h-dev).
     */
    @Bean
    public FlywayConfigurationCustomizer flywaySharedDatabaseCustomizer() {
        return configuration -> configuration.ignoreMigrationPatterns(
                "*:missing",
                "*:future");
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            boolean remapped = remapHdevVersionCollisions(dataSource);
            if (remapped) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    private static boolean remapHdevVersionCollisions(DataSource dataSource) {
        boolean remapped = false;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            for (VersionRemap remap : HDEV_COLLISION_REMAPS) {
                if (!scriptApplied(connection, remap.fromVersion(), remap.fromScript())) {
                    continue;
                }
                if (versionApplied(connection, remap.toVersion())) {
                    log.warn("Flyway already has version {}; leaving {} as-is",
                            remap.toVersion(), remap.fromScript());
                    continue;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                        UPDATE flyway_schema_history
                        SET version = ?, description = ?, script = ?, checksum = NULL
                        WHERE version = ? AND script = ?
                        """)) {
                    update.setString(1, remap.toVersion());
                    update.setString(2, descriptionFromScript(remap.toScript()));
                    update.setString(3, remap.toScript());
                    update.setString(4, remap.fromVersion());
                    update.setString(5, remap.fromScript());
                    int updated = update.executeUpdate();
                    if (updated > 0) {
                        remapped = true;
                        log.info("Remapped Flyway {} ({}) -> {} ({})",
                                remap.fromVersion(), remap.fromScript(),
                                remap.toVersion(), remap.toScript());
                    }
                }
            }
            connection.commit();
            return remapped;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to remap Flyway history after merge collision", ex);
        }
    }

    private static boolean scriptApplied(Connection connection, String version, String script) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM flyway_schema_history WHERE version = ? AND script = ? AND success = TRUE")) {
            query.setString(1, version);
            query.setString(2, script);
            try (ResultSet rs = query.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean versionApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = TRUE")) {
            query.setString(1, version);
            try (ResultSet rs = query.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String descriptionFromScript(String script) {
        String name = script.replaceFirst("^V\\d+__", "").replaceFirst("\\.sql$", "");
        return name.replace('_', ' ');
    }

    private record VersionRemap(String fromVersion, String fromScript, String toVersion, String toScript) {
    }
}
