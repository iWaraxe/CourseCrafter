package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This script extracts slide components from slides and creates appropriate SlideComponent entities.
 * It should run after the DatabasePopulationScript has created the basic content structure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3) // Ensure this runs after the main population script
@Transactional  // Add this at the class level
public class SlideComponentExtractor implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final SlideComponentRepository slideComponentRepository;
    private final SlideComponentService slideComponentService;
    private final ContentVersionRepository contentVersionRepository;

    // Inject a check for whether import is enabled
    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Skip if import is disabled
        if (databaseImportEnabled != null && !databaseImportEnabled) {
            log.info("Database import is disabled. Skipping slide component extraction.");
            return;
        }

        // Check if slide components already exist
        if (slideComponentRepository.count() > 0) {
            log.info("Slide components already exist. Skipping component extraction.");
            return;
        }

        log.info("Starting to extract slide components from slide content...");
        extractSlideComponents();
        log.info("Slide component extraction completed.");
    }

    protected void extractSlideComponents() {
        // Get all slide nodes
        List<ContentNode> slideNodes = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
        log.info("Found {} slide nodes to process", slideNodes.size());

        for (ContentNode slide : slideNodes) {
            processSlide(slide);
        }
    }

    protected void processSlide(ContentNode slide) {
        if (slide.getVersions() == null || slide.getVersions().isEmpty()) {
            log.warn("Slide has no versions: {} (ID: {})", slide.getTitle(), slide.getId());

            // Create a default version for this slide
            ContentVersion defaultVersion = ContentVersion.builder()
                    .node(slide)
                    .content(slide.getTitle())  // Use slide title as minimal content
                    .contentFormat("MARKDOWN")
                    .versionNumber(1)
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                // Save the new version
                contentVersionRepository.save(defaultVersion);

                // Refresh the slide to include the new version
                // This is important within the transaction
                contentNodeRepository.findById(slide.getId())
                        .ifPresent(refreshedSlide -> {
                            slide.setVersions(refreshedSlide.getVersions());
                            log.info("Created default version for slide: {}", slide.getTitle());
                        });
            } catch (Exception e) {
                log.error("Failed to create default version for slide {}: {}", slide.getId(), e.getMessage());
                return;
            }
        }

        // Determine component type based on slide title
        // Get content from the latest version
        String slideContent = slide.getVersions().stream()
                .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                .map(ContentVersion::getContent)
                .orElse("");


        // Debug slide components
        log.debug("Processing slide content for '{}': [Content length: {}]",
                slide.getTitle(), slideContent.length());

        log.debug("Processing slide content (length: {}): \n{}",
                slideContent.length(),
                slideContent.length() > 500 ? slideContent.substring(0, 500) + "..." : slideContent);


        // Define pattern to match component sections (level 6 headers)
        Matcher matcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(slideContent);

        // Add a counter for components
        int componentCount = 0;

        // Process each component found
        while (matcher.find()) {
            componentCount++;
            String componentTypeStr = matcher.group(1).trim();
            String componentContent = matcher.group(2).trim();

            // Map the header text directly to enum
            SlideComponent.ComponentType componentType;
            try {
                componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown component type '{}' in slide: {}", componentTypeStr, slide.getTitle());
                continue;
            }

            try {
                // Create the slide component
                SlideComponent component = slideComponentService.createComponent(
                        slide.getId(),
                        componentType,
                        componentContent
                );
                log.info("Created {} component for slide: {}", componentType, slide.getTitle());
            } catch (Exception e) {
                log.error("Failed to create component {} for slide {}: {}",
                        componentTypeStr, slide.getTitle(), e.getMessage());
            }
        }
        // Add debug logging after all components are processed
        log.debug("Found {} components in slide '{}'", componentCount, slide.getTitle());
    }

    private String extractVisualContent(String content) {
        // Try to extract table content (Markdown tables)
        Matcher tableMatcher = MarkdownPatterns.TABLE_PATTERN.matcher(content);

        StringBuilder tableContent = new StringBuilder();
        boolean foundTable = false;

        while (tableMatcher.find()) {
            tableContent.append(tableMatcher.group(0)).append("\n");
            foundTable = true;
        }

        if (foundTable) {
            return tableContent.toString();
        }

        // If no table, look for formatted text with asterisks or bullets
        Pattern formattedPattern = Pattern.compile("\\*\\*.+?\\*\\*|\\*[^*].+?\\*|\\- .+", Pattern.MULTILINE);
        Matcher formattedMatcher = formattedPattern.matcher(content);

        StringBuilder formattedContent = new StringBuilder();
        boolean foundFormatted = false;

        while (formattedMatcher.find()) {
            formattedContent.append(formattedMatcher.group(0)).append("\n");
            foundFormatted = true;
        }

        if (foundFormatted) {
            return formattedContent.toString();
        }

        // If nothing specific found, return original content
        return content;
    }
}