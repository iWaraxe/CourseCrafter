package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import jakarta.transaction.Transactional;
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
@Order(3)
public class SlideComponentExtractor implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final SlideComponentRepository slideComponentRepository;
    private final SlideComponentService slideComponentService;

    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    // Improved component pattern that captures content more reliably
    private static final Pattern IMPROVED_COMPONENT_PATTERN = Pattern.compile(
            "^######\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*$(\\r?\\n|\\r)(.*?)(?=\\r?\\n######\\s+(?:SCRIPT|VISUAL|NOTES|DEMONSTRATION)|\\Z)",
            Pattern.MULTILINE | Pattern.DOTALL
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (databaseImportEnabled != null && !databaseImportEnabled) {
            log.info("Database import is disabled. Skipping slide component extraction by SlideComponentExtractor.");
            return;
        }

        if (slideComponentRepository.count() > 0) {
            log.info("Slide components seem to already exist ({} found). SlideComponentExtractor will skip its run.", slideComponentRepository.count());
            log.info("If components are missing or incorrect, ensure MarkdownCourseParser is functioning as expected or clear the slide_component table for a fresh extraction.");
            return;
        }

        log.info("SlideComponentExtractor: Starting to extract slide components (this run implies MarkdownCourseParser did not create them or they were cleared).");
        extractSlideComponentsFromContentNodes();
        log.info("SlideComponentExtractor: Slide component extraction completed.");
    }

    @Transactional
    protected void extractSlideComponentsFromContentNodes() {
        List<ContentNode> slideNodes = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
        log.info("SlideComponentExtractor: Found {} slide nodes to process for components.", slideNodes.size());

        for (ContentNode slide : slideNodes) {
            Optional<String> latestVersionContentOpt = slide.getVersions().stream()
                    .max((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber()))
                    .map(ContentVersion::getContent);

            if (latestVersionContentOpt.isEmpty()) {
                log.warn("SlideComponentExtractor: Slide '{}' (ID: {}) has no versions or content in version. Cannot extract components.", slide.getTitle(), slide.getId());
                continue;
            }

            String fullSlideMarkdown = latestVersionContentOpt.get();
            log.debug("SlideComponentExtractor: Processing slide '{}' (ID: {}) for components. Full Markdown length: {}",
                    slide.getTitle(), slide.getId(), fullSlideMarkdown.length());

            // Extract the BODY of the slide using SLIDE_PATTERN (Group 3)
            Matcher slidePatternMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(fullSlideMarkdown);
            String slideBodyForComponents = "";

            if (slidePatternMatcher.find()) {
                slideBodyForComponents = slidePatternMatcher.group(3) != null ? slidePatternMatcher.group(3).trim() : "";
                log.debug("SlideComponentExtractor: Extracted slide body for components (length {}). Title matched: '{}'",
                        slideBodyForComponents.length(), slidePatternMatcher.group(2).trim());
            } else {
                log.warn("SlideComponentExtractor: Could not re-match SLIDE_PATTERN for slide content of '{}' (ID: {}). Attempting manual strip.",
                        slide.getTitle(), slide.getId());
                // Fallback: try to strip the H5 line manually (less robust)
                int firstNewline = fullSlideMarkdown.indexOf('\n');
                if (firstNewline != -1 && fullSlideMarkdown.trim().startsWith("#####")) {
                    slideBodyForComponents = fullSlideMarkdown.substring(firstNewline + 1).trim();
                    log.debug("SlideComponentExtractor: Manually stripped header, body length: {}", slideBodyForComponents.length());
                } else {
                    log.error("SlideComponentExtractor: Failed to extract body from slide content for '{}'. Full content was: {}", slide.getTitle(), fullSlideMarkdown.substring(0, Math.min(100, fullSlideMarkdown.length())));
                    continue; // Skip this slide
                }
            }

            if (slideBodyForComponents.isEmpty()) {
                log.debug("SlideComponentExtractor: Slide '{}' (ID: {}) has no body content after header extraction. No components to process.", slide.getTitle(), slide.getId());
                continue;
            }

            // Now, parse components using the improved pattern
            Matcher componentMatcher = IMPROVED_COMPONENT_PATTERN.matcher(slideBodyForComponents);
            int componentsFoundInThisSlide = 0;

            while (componentMatcher.find()) {
                componentsFoundInThisSlide++;
                String componentTypeStr = componentMatcher.group(1).trim().toUpperCase();
                String componentContent = componentMatcher.group(3) != null ? componentMatcher.group(3).trim() : "";

                log.debug("SlideComponentExtractor: Found component of type '{}' with content length={}. First 50 chars: '{}'",
                        componentTypeStr,
                        componentContent.length(),
                        componentContent.length() > 50 ? componentContent.substring(0, 50) + "..." : componentContent);

                SlideComponent.ComponentType componentType;
                try {
                    componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("SlideComponentExtractor: Unknown component type: '{}' in slide: {}. Skipping component.", componentTypeStr, slide.getTitle());
                    continue;
                }

                // Check if this specific component already exists (e.g. if script is run multiple times)
                boolean componentExists = slideComponentRepository.findBySlideIdAndType(slide.getId(), componentType).isPresent();
                if (componentExists) {
                    log.debug("SlideComponentExtractor: {} component for slide '{}' already exists. Skipping creation.", componentType, slide.getTitle());
                    continue;
                }

                try {
                    slideComponentService.createComponent(slide.getId(), componentType, componentContent);
                    log.info("SlideComponentExtractor: Created {} component for slide: {}", componentType, slide.getTitle());
                } catch (Exception e) {
                    log.error("SlideComponentExtractor: Failed to create component {} for slide {}: {}",
                            componentTypeStr, slide.getTitle(), e.getMessage(), e);
                }
            }

            if (componentsFoundInThisSlide == 0) {
                log.warn("SlideComponentExtractor: No H6 components found in body of slide: '{}' (ID: {}). Slide body length: {}",
                        slide.getTitle(), slide.getId(), slideBodyForComponents.length());
                if (!slideBodyForComponents.trim().isEmpty() && !slideBodyForComponents.contains("######")) {
                    log.warn("SlideComponentExtractor: Slide '{}' body content (first 100 chars): '{}'",
                            slide.getTitle(), slideBodyForComponents.substring(0, Math.min(slideBodyForComponents.length(),100)));
                }
            } else {
                log.info("SlideComponentExtractor: Found and processed {} components in slide: {}", componentsFoundInThisSlide, slide.getTitle());
            }
        }
    }
}