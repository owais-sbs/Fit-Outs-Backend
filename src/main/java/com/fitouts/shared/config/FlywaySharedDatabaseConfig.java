package com.fitouts.shared.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywaySharedDatabaseConfig {

    /**
     * Shared RDS has migrations from other branches. Community Flyway validate fails when those
     * files are missing. Repair realigns checksums when an applied migration file was rewritten
     * locally (e.g. after V55/V56/V57 were renamed or merged on another branch).
     */
    @Bean
    public FlywayConfigurationCustomizer flywaySharedDatabaseCustomizer() {
        return configuration -> configuration.ignoreMigrationPatterns(
                "*:missing",
                "*:future");
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
