package com.fitouts.shared.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.flywaydb.core.Flyway;

@Configuration
public class FlywaySharedDatabaseConfig {

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
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return Flyway::migrate;
    }
    
    }

