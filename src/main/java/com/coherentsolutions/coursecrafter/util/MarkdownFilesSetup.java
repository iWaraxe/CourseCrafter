package com.coherentsolutions.coursecrafter.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

/**
 * Utility to copy course content markdown files to the application resources directory.
 * This ensures the files are available when the DatabasePopulationScript runs.
 */
@Component
@Slf4j
@Order(1) // Make sure this runs before database population
public class MarkdownFilesSetup implements CommandLineRunner {

    // Default content directory
    @Value("${coursecrafter.import.folder:src/main/resources/course_content}")
    private String courseContentDir;

    // Inject a check for whether import is enabled
    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    @Override
    public void run(String... args) throws Exception {
        // Skip if import is disabled
        if (databaseImportEnabled != null && !databaseImportEnabled) {
            log.info("Database import is disabled. Skipping markdown file setup.");
            return;
        }

        Path contentDir = Paths.get(courseContentDir);

        // Create directory if it doesn't exist
        if (!Files.exists(contentDir)) {
            try {
                Files.createDirectories(contentDir);
                log.info("Created course content directory: {}", contentDir);
            } catch (IOException e) {
                log.error("Failed to create course content directory", e);
                return;
            }
        }

        // Check if there are any markdown files already present
        if (Files.list(contentDir).anyMatch(p -> p.toString().endsWith(".md"))) {
            log.info("Markdown files already exist in course content directory. Skipping setup.");
            return;
        }

        log.info("Setting up markdown files for course content...");

        // Copy lecture files from source to resources directory
        copyLectureFiles(contentDir);

        log.info("Markdown file setup completed.");
    }

    private void copyLectureFiles(Path targetDir) {
        // Define potential source directories where your lecture files might be
        String[] possibleSourceDirs = {
                "course_content",                         // Root of project
                "src/main/resources",                    // Resources
                System.getProperty("user.dir") + "/course_content", // Current working directory
                System.getProperty("user.home") + "/course_content" // User home directory
        };

        // Try to find and copy files from any of these directories
        for (String sourceDir : possibleSourceDirs) {
            Path sourcePath = Paths.get(sourceDir);
            if (Files.exists(sourcePath) && Files.isDirectory(sourcePath)) {
                try {
                    long count = Files.list(sourcePath)
                            .filter(p -> p.toString().endsWith(".md") &&
                                    p.getFileName().toString().contains("Lecture"))
                            .peek(p -> {
                                try {
                                    Path targetFile = targetDir.resolve(p.getFileName());
                                    Files.copy(p, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                    log.info("Copied {} to {}", p.getFileName(), targetFile);
                                } catch (IOException e) {
                                    log.error("Failed to copy file: {}", p.getFileName(), e);
                                }
                            })
                            .count();

                    // If we've found files to copy, break out of the loop
                    if (count > 0) {
                        log.info("Found and copied {} markdown files from {}", count, sourcePath);
                        break;
                    }
                } catch (IOException e) {
                    log.error("Failed to process directory: {}", sourceDir, e);
                }
            }
        }

        // Check if we found any files
        try {
            long fileCount = Files.list(targetDir)
                    .filter(p -> p.toString().endsWith(".md"))
                    .count();

            if (fileCount == 0) {
                log.warn("No markdown files were found or copied. Please manually place your markdown files in: {}", targetDir);
            } else {
                log.info("Successfully copied {} markdown files to the target directory", fileCount);
            }
        } catch (IOException e) {
            log.error("Failed to count files in target directory", e);
        }
    }
}