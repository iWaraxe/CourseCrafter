package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
// Unused import: com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;

// Import for @Transactional - Spring's annotation is generally preferred
import org.springframework.transaction.annotation.Transactional;
// If you were using jakarta.transaction.Transactional, that would be fine too, but be consistent.

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(3) // Ensure this runs after MarkdownCourseParser or main population
public class SlideComponentExtractor implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final SlideComponentRepository slideComponentRepository;
    private final SlideComponentService slideComponentService;

    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    // Improved component pattern that captures content more reliably
    // Group 1: Component type (SCRIPT, VISUAL, NOTES, DEMONSTRATION)
    // Group 2: Newline character(s) right after the H6 header line
    // Group 3: Component content (everything until the next component or end of input)
    private static final Pattern IMPROVED_COMPONENT_PATTERN = Pattern.compile(
            "^######\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*$(\\r?\\n|\\r)(.*?)(?=\\r?\\n######\\s+(?:SCRIPT|VISUAL|NOTES|DEMONSTRATION)|\\Z)",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    @Override
    @Transactional // Use Spring's @Transactional for data consistency
    public void run(String... args) throws Exception {
        if (databaseImportEnabled != null && !databaseImportEnabled) {
            log.info("Database import is disabled. Skipping slide component extraction by SlideComponentExtractor.");
            return;
        }

        // Check if components were already created (e.g., by MarkdownCourseParser).
        if (slideComponentRepository.count() > 0) {
            log.info("Slide components seem to already exist ({} found). SlideComponentExtractor will skip its run.", slideComponentRepository.count());
            log.info("If components are missing or incorrect, ensure MarkdownCourseParser is functioning as expected or clear the slide_component table for a fresh extraction.");
            return;
        }

        log.info("SlideComponentExtractor: Starting to extract slide components (this run implies MarkdownCourseParser did not create them or they were cleared).");
        extractSlideComponentsFromContentNodes();
        log.info("SlideComponentExtractor: Slide component extraction completed.");
    }

    @Transactional // Ensure operations within are transactional
    protected void extractSlideComponentsFromContentNodes() {
        List<ContentNode> allSlideNodes = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
        log.info("SlideComponentExtractor: Found {} slide nodes to process for components.", allSlideNodes.size());

        for (ContentNode currentSlideNode : allSlideNodes) { // Renamed 'slide' to 'currentSlideNode' for clarity
            // Get the latest version's content, which MarkdownCourseParser should have stored.
            Optional<String> latestVersionContentOpt = currentSlideNode.getVersions().stream()
                    .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                    .map(ContentVersion::getContent);

            if (latestVersionContentOpt.isEmpty()) {
                log.warn("SlideComponentExtractor: Slide '{}' (ID: {}) has no versions or content in version. Cannot extract components.", currentSlideNode.getTitle(), currentSlideNode.getId());
                continue;
            }

            String fullSlideMarkdown = latestVersionContentOpt.get();
            log.debug("SlideComponentExtractor: Processing slide '{}' (ID: {}) for components. Full Markdown length: {}",
                    currentSlideNode.getTitle(), currentSlideNode.getId(), fullSlideMarkdown.length());

            // Extract the BODY of the slide using MarkdownPatterns.SLIDE_PATTERN (Group 3)
            // Assuming MarkdownPatterns.SLIDE_PATTERN is defined elsewhere and correct
            Matcher slidePatternMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(fullSlideMarkdown);
            String slideBodyForComponents;

            if (slidePatternMatcher.find()) {
                slideBodyForComponents = slidePatternMatcher.group(3) != null ? slidePatternMatcher.group(3).trim() : "";
                log.debug("SlideComponentExtractor: Extracted slide body for components (length {}). Title matched: '{}'",
                        slideBodyForComponents.length(), slidePatternMatcher.group(2).trim());
            } else {
                log.warn("SlideComponentExtractor: Could not re-match SLIDE_PATTERN for slide content of '{}' (ID: {}). Attempting manual strip.",
                        currentSlideNode.getTitle(), currentSlideNode.getId());
                // Fallback: try to strip the H5 line manually (less robust)
                int firstNewline = fullSlideMarkdown.indexOf('\n');
                if (firstNewline != -1 && fullSlideMarkdown.trim().startsWith("#####")) {
                    slideBodyForComponents = fullSlideMarkdown.substring(firstNewline + 1).trim();
                    log.debug("SlideComponentExtractor: Manually stripped header, body length: {}", slideBodyForComponents.length());
                } else {
                    log.error("SlideComponentExtractor: Failed to extract body from slide content for '{}'. Full content preview: '{}'",
                            currentSlideNode.getTitle(), fullSlideMarkdown.substring(0, Math.min(100, fullSlideMarkdown.length())));
                    continue; // Skip this slide
                }
            }

            if (slideBodyForComponents.isEmpty()) {
                log.debug("SlideComponentExtractor: Slide '{}' (ID: {}) has no body content after header extraction. No components to process.", currentSlideNode.getTitle(), currentSlideNode.getId());
                continue;
            }

            // Now, parse components from the extracted slideBodyForComponents using the local IMPROVED_COMPONENT_PATTERN
            Matcher componentMatcher = IMPROVED_COMPONENT_PATTERN.matcher(slideBodyForComponents);
            int componentsFoundInThisSlide = 0;
            while (componentMatcher.find()) {
                componentsFoundInThisSlide++;
                String componentTypeStr = componentMatcher.group(1).trim().toUpperCase();
                // Content is in Group 3 of IMPROVED_COMPONENT_PATTERN
                String componentContent = componentMatcher.group(3) != null ? componentMatcher.group(3).trim() : "";

                log.debug("SlideComponentExtractor: For slide '{}', found component type='{}', extracted content length={}. Preview (first 50 chars, newlines as \\n): '{}'",
                        currentSlideNode.getTitle(),
                        componentTypeStr,
                        componentContent.length(),
                        componentContent.substring(0, Math.min(componentContent.length(), 50)).replace("\n", "\\n"));

                SlideComponent.ComponentType componentType;
                try {
                    componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("SlideComponentExtractor: Unknown component type: '{}' in slide: {}. Skipping component.", componentTypeStr, currentSlideNode.getTitle());
                    continue;
                }

                // Check if this specific component already exists
                // The variable 'currentSlideNode' holds the ContentNode for the current slide.
                boolean componentExists = slideComponentRepository.findBySlideIdAndType(currentSlideNode.getId(), componentType).isPresent();
                if (componentExists) {
                    log.debug("SlideComponentExtractor: {} component for slide '{}' (ID: {}) already exists. Skipping creation.",
                            componentType, currentSlideNode.getTitle(), currentSlideNode.getId());
                    continue;
                }

                try {
                    slideComponentService.createComponent(currentSlideNode.getId(), componentType, componentContent);
                    log.info("SlideComponentExtractor: Created {} component for slide: {}", componentType, currentSlideNode.getTitle());
                } catch (Exception e) {
                    log.error("SlideComponentExtractor: Failed to create component {} for slide {} (ID: {}): {}",
                            componentTypeStr, currentSlideNode.getTitle(), currentSlideNode.getId(), e.getMessage(), e);
                }
            }

            if (componentsFoundInThisSlide == 0) {
                log.warn("SlideComponentExtractor: No H6 components found in body of slide: '{}' (ID: {}). Slide body length: {}",
                        currentSlideNode.getTitle(), currentSlideNode.getId(), slideBodyForComponents.length());
                if (!slideBodyForComponents.trim().isEmpty() && !slideBodyForComponents.contains("######")) {
                    log.warn("SlideComponentExtractor: Slide '{}' body content (first 100 chars, newlines as \\n): '{}'",
                            currentSlideNode.getTitle(),
                            slideBodyForComponents.substring(0, Math.min(slideBodyForComponents.length(),100)).replace("\n", "\\n"));
                }
            } else {
                log.info("SlideComponentExtractor: Found and processed {} components in slide: {}", componentsFoundInThisSlide, currentSlideNode.getTitle());
            }
        }
    }
}