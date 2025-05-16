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
import org.springframework.transaction.annotation.Propagation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

        // Force a short delay to ensure all DB operations are complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    @Transactional
    protected void processSlide(ContentNode slide) {
        if (slide.getVersions() == null || slide.getVersions().isEmpty()) {
            log.warn("Slide has no versions: {} (ID: {})", slide.getTitle(), slide.getId());

            // This will hold our full slide content
            String fullSlideContent = "";

            // STEP 1: Try to find the original content in the markdown files
            try {
                Path courseContentDir = Paths.get("course_content");
                final String slideTitle = slide.getTitle();

                if (Files.exists(courseContentDir)) {
                    // Use DirectoryStream instead of Files.list to avoid lambda issues
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(courseContentDir, "*.md")) {
                        for (Path path : stream) {
                            String fileContent = Files.readString(path);

                            // Look for slides with matching title
                            Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(fileContent);
                            while (slideMatcher.find()) {
                                String foundTitle = slideMatcher.group(2).trim();

                                // If title matches, grab the full content
                                if (foundTitle.equals(slideTitle)) {
                                    fullSlideContent = slideMatcher.group(3).trim();
                                    log.debug("Found original content for slide '{}' in file {}",
                                            slideTitle, path.getFileName());
                                    break;
                                }
                            }

                            // If content found, no need to check more files
                            if (!fullSlideContent.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error finding original slide content: {}", e.getMessage());
            }

            // STEP 2: If no content found, create a complete template with all component sections
            if (fullSlideContent.isEmpty()) {
                log.info("Creating template content for slide: {}", slide.getTitle());
                fullSlideContent = String.format("""
                ###### SCRIPT
                This is a template script for slide: %s
                
                ###### VISUAL
                This is a template visual content.
                
                ###### NOTES
                These are template notes.
                
                ###### DEMONSTRATION
                This is a template demonstration.
                """, slide.getTitle());
            }

            // After creating the version content
            log.debug("Full template content created: \n{}", fullSlideContent);

            // When processing component matcher
            String slideContent = slide.getVersions().stream()
                    .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                    .map(ContentVersion::getContent)
                    .orElse("");

            log.debug("Processing slide content for '{}' (length: {})",
                    slide.getTitle(), slideContent.length());

            log.debug("Content being searched for components:\n{}", slideContent);
            log.debug("Using regex pattern: {}", MarkdownPatterns.COMPONENT_PATTERN.pattern());

            // Preprocess the content to ensure component headers are properly formatted
            String processedContent = slideContent.replaceAll("\n[ \t]*######[ \t]*", "\n###### ");

            // Test the pattern explicitly
            // Add a newline at the beginning to help with regex matching
            Matcher componentMatcher = MarkdownPatterns.COMPONENT_PATTERN.matcher("\n" + processedContent);
            int componentCount = 0;

            // Find all components
            while (componentMatcher.find()) {
                componentCount++;

                String componentTypeStr = componentMatcher.group(1).trim();
                String componentContent = componentMatcher.group(2).trim();

                log.debug("Found {} component with {} characters of content",
                        componentTypeStr, componentContent.length());

                // Map string to enum
                SlideComponent.ComponentType componentType;
                try {
                    componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown component type: {} in slide: {}", componentTypeStr, slide.getTitle());
                    continue;
                }

                try {
                    // Create the component
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

            if (componentCount == 0) {
                log.warn("No components found in slide: {}", slide.getTitle());
            } else {
                log.info("Processed {} components for slide: {}", componentCount, slide.getTitle());
            }

            log.debug("TOTAL MATCHES: {}", componentCount);

            // Create the version with our full content
            ContentVersion defaultVersion = ContentVersion.builder()
                    .node(slide)
                    .content(fullSlideContent)
                    .contentFormat("MARKDOWN")
                    .versionNumber(1)
                    .createdAt(LocalDateTime.now())
                    .build();



            try {
                // Save the new version
                contentVersionRepository.save(defaultVersion);

                // Refresh the slide to include the new version
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

        // Now process components from the slide's latest version content
        String slideContent = slide.getVersions().stream()
                .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                .map(ContentVersion::getContent)
                .orElse("");

        // Add detailed debugging to see what's happening
        log.debug("Processing slide content for '{}' [Length: {}]",
                slide.getTitle(), slideContent.length());

        if (slideContent.length() > 300) {
            log.debug("Content excerpt: {}...", slideContent.substring(0, 300));
        } else {
            log.debug("Full content: {}", slideContent);
        }

        // Look for components in the slide content
        Matcher componentMatcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(slideContent);
        int componentCount = 0;

        while (componentMatcher.find()) {
            componentCount++;
            String componentTypeStr = componentMatcher.group(1).trim();
            String componentContent = componentMatcher.group(2).trim();

            log.debug("Found component of type {} with content length: {}",
                    componentTypeStr, componentContent.length());

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

        if (componentCount == 0) {
            log.warn("No components found in slide: {}", slide.getTitle());
        } else {
            log.info("Found and processed {} components in slide: {}", componentCount, slide.getTitle());
        }
    }
}