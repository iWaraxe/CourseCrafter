package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
public class SlideComponentExtractor implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final SlideComponentRepository slideComponentRepository;
    private final SlideComponentService slideComponentService;

    // Inject a check for whether import is enabled
    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    @Override
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

    private void extractSlideComponents() {
        // Get all slide nodes
        List<ContentNode> slideNodes = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
        log.info("Found {} slide nodes to process", slideNodes.size());

        for (ContentNode slide : slideNodes) {
            processSlide(slide);
        }
    }

    private void processSlide(ContentNode slide) {
        // Determine component type based on slide title
        String slideTitle = slide.getTitle().toLowerCase();
        SlideComponent.ComponentType componentType;

        if (slideTitle.contains("script")) {
            componentType = SlideComponent.ComponentType.SCRIPT;
        } else if (slideTitle.contains("demo") || slideTitle.contains("demonstration") ||
                slideTitle.contains("instructions")) {
            componentType = SlideComponent.ComponentType.DEMONSTRATION;
        } else if (slideTitle.contains("slide")) {
            componentType = SlideComponent.ComponentType.VISUAL;
        } else if (slideTitle.contains("note")) {
            componentType = SlideComponent.ComponentType.NOTES;
        } else {
            // Default type if we can't determine
            componentType = SlideComponent.ComponentType.NOTES;
        }

        // Get content from the slide's latest version if available
        if (slide.getVersions() == null || slide.getVersions().isEmpty()) {
            log.warn("Slide has no versions: {}", slide.getTitle());
            return;
        }

        slide.getVersions().stream()
                .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                .ifPresent(latestVersion -> {
                    String content = latestVersion.getContent();

                    // Extract component-specific content
                    if (componentType == SlideComponent.ComponentType.VISUAL) {
                        // For visual slides, try to extract table content or formatted content
                        content = extractVisualContent(content);
                    }

                    // Create the slide component
                    try {
                        SlideComponent component = slideComponentService.createComponent(
                                slide.getId(),
                                componentType,
                                content
                        );

                        log.info("Created {} component for slide: {}", componentType, slide.getTitle());
                    } catch (Exception e) {
                        log.error("Failed to create component for slide {}: {}", slide.getTitle(), e.getMessage());
                    }
                });
    }

    private String extractVisualContent(String content) {
        // Try to extract table content (Markdown tables)
        Pattern tablePattern = Pattern.compile("\\|(.+?)\\|", Pattern.DOTALL);
        Matcher tableMatcher = tablePattern.matcher(content);

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