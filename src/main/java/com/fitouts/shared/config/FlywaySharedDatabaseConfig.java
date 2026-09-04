package com.fitouts.shared.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywaySharedDatabaseConfig {

    /**
     * Shared RDS has migrations from other branches. Community Flyway validate fails when those
     * files are missing or the checksum differs. Do not enable outOfOrder or repair history.
     */
    @Bean
    public FlywayConfigurationCustomizer flywaySharedDatabaseCustomizer() {
        return configuration -> configuration.ignoreMigrationPatterns(
                "*:missing",
                "*:future");
    }
}
