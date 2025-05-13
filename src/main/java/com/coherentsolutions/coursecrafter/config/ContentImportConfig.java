package com.coherentsolutions.coursecrafter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for database initialization and content import.
 * Only active in dev profile to prevent accidental import in production.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev") // Only active in development profile
public class ContentImportConfig {

    private final Environment environment;

    /**
     * This bean controls whether database population scripts should run.
     * Controlled by the 'coursecrafter.import.enabled' property.
     */
    @Bean
    public boolean databaseImportEnabled() {
        boolean importEnabled = Boolean.parseBoolean(
                environment.getProperty("coursecrafter.import.enabled", "false"));

        log.info("Database import is {}", importEnabled ? "enabled" : "disabled");

        if (importEnabled) {
            log.info("Database population scripts will run on startup");
        } else {
            log.info("Database population scripts are disabled. Set coursecrafter.import.enabled=true to enable");
        }

        return importEnabled;
    }
}