package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkdownCourseParser {

    private final ContentNodeRepository contentNodeRepository;
    private final ContentNodeService contentNodeService;
    private final SlideComponentService slideComponentService;

    /**
     * Parse a markdown file and create the entire course hierarchy
     */
    public void parseFile(Path filePath) throws IOException, InterruptedException {
        log.info("Parsing markdown file: {}", filePath.getFileName());
        String content = Files.readString(filePath);

        // DEBUG: Print the entire file content for inspection
        log.debug("==================== FILE CONTENT ====================");
        log.debug(content);
        log.debug("====================================================");

        // Parse course
        ContentNode courseNode = parseCourse(content);
        if (courseNode == null) {
            log.error("No course found in file: {}", filePath);
            return;
        }

        // Parse lectures under the course
        parseLectures(content, courseNode);

        // Also check for slides directly under the course (unusual but possible)
        parseDirectSlides(content, courseNode);
    }

    /**
     * Parse the course level (h1)
     */
    private ContentNode parseCourse(String content) throws IOException, InterruptedException {
        Matcher courseMatcher = MarkdownPatterns.COURSE_PATTERN.matcher(content);
        if (!courseMatcher.find()) {
            return null;
        }

        String courseTitle = courseMatcher.group(1).trim();

        // Check if course with this title already exists
        Optional<ContentNode> existingCourse = contentNodeRepository.findByNodeTypeAndTitle(ContentNode.NodeType.COURSE, courseTitle);

        if (existingCourse.isPresent()) {
            log.info("Found existing course: {} (ID: {})", courseTitle, existingCourse.get().getId());
            return existingCourse.get();
        }

        // If no existing course, create a new one
        log.info("Creating new course: {}", courseTitle);
        ContentNode courseNode = ContentNode.builder()
                .nodeType(ContentNode.NodeType.COURSE)
                .title(courseTitle)
                .displayOrder(1)
                .path("Course/" + UUID.randomUUID().toString().substring(0, 8))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return contentNodeService.createNode(courseNode,
                "# " + courseTitle,
                "Initial course creation");
    }

    /**
     * Parse lectures (h2) under the course
     */
    private void parseLectures(String content, ContentNode courseNode) throws IOException, InterruptedException {
        Matcher lectureMatcher = MarkdownPatterns.LECTURE_PATTERN.matcher(content);

        while (lectureMatcher.find()) {
            String lectureTitle = lectureMatcher.group(1).trim();

            // Extract lecture number from title (e.g., "Lecture 1.")
            int lectureNumber = 1; // Default
            Pattern pattern = Pattern.compile("Lecture (\\d+)\\.");
            Matcher matcher = pattern.matcher(lectureTitle);
            if (matcher.find()) {
                lectureNumber = Integer.parseInt(matcher.group(1));
            }

            // Set display order based on lecture number (multiply by 10 for spacing)
            int displayOrder = lectureNumber * 10;

            // Find the start position of this lecture
            int lectureStart = lectureMatcher.start();

            // Find where this lecture ends (next lecture or EOF)
            int lectureEnd = content.length();
            lectureMatcher.region(lectureMatcher.end(), content.length());
            if (lectureMatcher.find()) {
                lectureEnd = lectureMatcher.start();
                // Reset the region for the next iteration
                lectureMatcher.region(lectureEnd, content.length());
            } else {
                // Reset matcher to continue from where we left off
                lectureMatcher = MarkdownPatterns.LECTURE_PATTERN.matcher(content);
                lectureMatcher.region(lectureStart + 1, content.length());
            }

            // Extract lecture content
            String lectureContent = content.substring(lectureStart, lectureEnd);

            // Create lecture node
            ContentNode lectureNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.LECTURE)
                    .parent(courseNode)
                    .title(lectureTitle)
                    .displayOrder(displayOrder)
                    .path(courseNode.getPath() + "/Lecture/" + lectureNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            lectureNode = contentNodeService.createNode(lectureNode,
                    lectureContent,
                    "Created lecture: " + lectureTitle);
            log.info("Created lecture: {} with order {}", lectureTitle, displayOrder);

            // Parse sections within the lecture
            parseSections(lectureContent, lectureNode);

            // Parse topics directly under the lecture (skip section level)
            parseTopicsDirectly(lectureContent, lectureNode);

            // Parse slides directly under the lecture (skip section and topic levels)
            parseDirectSlides(lectureContent, lectureNode);
        }
    }

    /**
     * Parse sections (h3) under a lecture
     */
    private void parseSections(String content, ContentNode lectureNode) throws IOException, InterruptedException {
        Matcher sectionMatcher = MarkdownPatterns.SECTION_PATTERN.matcher(content);
        int sectionOrder = 10;

        while (sectionMatcher.find()) {
            String sectionTitle = sectionMatcher.group(1).trim();

            // Find the start position of this section
            int sectionStart = sectionMatcher.start();

            // Find where this section ends (next section or EOF)
            int sectionEnd = content.length();
            sectionMatcher.region(sectionMatcher.end(), content.length());
            if (sectionMatcher.find()) {
                sectionEnd = sectionMatcher.start();
                // Reset the region for the next iteration
                sectionMatcher.region(sectionEnd, content.length());
            } else {
                // Reset matcher to continue from where we left off
                sectionMatcher = MarkdownPatterns.SECTION_PATTERN.matcher(content);
                sectionMatcher.region(sectionStart + 1, content.length());
            }

            // Extract section content
            String sectionContent = content.substring(sectionStart, sectionEnd);

            // Create section node
            ContentNode sectionNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SECTION)
                    .parent(lectureNode)
                    .title(sectionTitle)
                    .displayOrder(sectionOrder)
                    .path(lectureNode.getPath() + "/Section/" + sectionOrder)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sectionNode = contentNodeService.createNode(sectionNode,
                    sectionContent,
                    "Created section: " + sectionTitle);
            log.info("Created section: {} with order {}", sectionTitle, sectionOrder);

            // Parse topics within the section
            parseTopics(sectionContent, sectionNode);

            // Also check for slides directly under the section
            parseSlides(sectionContent, sectionNode);

            sectionOrder += 10;
        }
    }

    /**
     * Parse topics (h4) under a section
     */
    private void parseTopics(String content, ContentNode sectionNode) throws IOException, InterruptedException {
        Matcher topicMatcher = MarkdownPatterns.TOPIC_PATTERN.matcher(content);
        int topicOrder = 10;

        while (topicMatcher.find()) {
            String topicTitle = topicMatcher.group(1).trim();

            // Find the start position of this topic
            int topicStart = topicMatcher.start();

            // Find where this topic ends (next topic or EOF)
            int topicEnd = content.length();
            topicMatcher.region(topicMatcher.end(), content.length());
            if (topicMatcher.find()) {
                topicEnd = topicMatcher.start();
                // Reset the region for the next iteration
                topicMatcher.region(topicEnd, content.length());
            } else {
                // Reset matcher to continue from where we left off
                topicMatcher = MarkdownPatterns.TOPIC_PATTERN.matcher(content);
                topicMatcher.region(topicStart + 1, content.length());
            }

            // Extract topic content
            String topicContent = content.substring(topicStart, topicEnd);

            // Create topic node
            ContentNode topicNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.TOPIC)
                    .parent(sectionNode)
                    .title(topicTitle)
                    .displayOrder(topicOrder)
                    .path(sectionNode.getPath() + "/Topic/" + topicOrder)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            topicNode = contentNodeService.createNode(topicNode,
                    topicContent,
                    "Created topic: " + topicTitle);
            log.info("Created topic: {} with order {}", topicTitle, topicOrder);

            // Parse slides under the topic
            parseSlides(topicContent, topicNode);

            topicOrder += 10;
        }
    }

    /**
     * Parse slides (h5) with sequence numbers under any parent node
     */
    private void parseSlides(String content, ContentNode parentNode) throws IOException, InterruptedException {
        Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(content);

        log.debug("Looking for slides in content of length: {}", content.length());

        while (slideMatcher.find()) {
            String seqNumber = slideMatcher.group(1).trim();
            String slideTitle = slideMatcher.group(2).trim();
            String slideContent = slideMatcher.group(3).trim();

            log.debug("FOUND SLIDE: seq={}, title={}, content length={}",
                    seqNumber, slideTitle, slideContent.length());

            // Manual check for existing slides with the same title and sequence
            boolean slideExists = false;
            int displayOrder = Integer.parseInt(seqNumber);

            // Use existing methods to query
            List<ContentNode> existingSlides = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
            for (ContentNode node : existingSlides) {
                if (node.getTitle().equals(slideTitle) && node.getDisplayOrder() == displayOrder) {
                    slideExists = true;
                    log.info("Slide '{}' with sequence {} already exists (ID: {}), skipping",
                            slideTitle, seqNumber, node.getId());
                    break;
                }
            }

            if (slideExists) {
                continue; // Skip to the next slide
            }


            // Create slide node with sequence-based display order
            ContentNode slideNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SLIDE)
                    .parent(parentNode)
                    .title(slideTitle)
                    .displayOrder(Integer.parseInt(seqNumber))
                    .path(parentNode.getPath() + "/Slide/" + seqNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            try {
                // Save and commit the slide node
                slideNode = contentNodeService.createNode(slideNode,
                        slideContent,
                        "Created slide: " + slideTitle);

                log.info("Created slide: {} with sequence {}", slideTitle, seqNumber);

                // Process components within this slide
                processSlideComponents(slideContent, slideNode);
            } catch (Exception e) {
                log.error("Failed to create slide {}: {}", slideTitle, e.getMessage());
            }
        }
    }

    private void processSlideComponents(String slideContent, ContentNode slideNode) {
        log.debug("Processing slide content (length: {}): \n{}", slideContent.length(), slideContent);

        // Create the component matcher with our updated pattern
        Matcher componentMatcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(slideContent);

        int componentCount = 0;

        // Find all components in the slide content
        while (componentMatcher.find()) {
            componentCount++;

            String componentTypeStr = componentMatcher.group(1).trim();
            String componentContent = componentMatcher.group(2).trim();

            log.debug("Found component type: {} with content: {}",
                    componentTypeStr,
                    componentContent.length() > 30 ?
                            componentContent.substring(0, 30) + "..." :
                            componentContent);

            // Convert string to enum
            SlideComponent.ComponentType componentType;
            try {
                componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown component type: {} in slide: {}", componentTypeStr, slideNode.getTitle());
                continue;
            }

            try {
                // Create the component
                SlideComponent component = slideComponentService.createComponent(
                        slideNode.getId(),
                        componentType,
                        componentContent
                );

                log.info("Created {} component for slide: {}", componentType, slideNode.getTitle());
            } catch (Exception e) {
                log.error("Failed to create component {} for slide {}: {}",
                        componentTypeStr, slideNode.getTitle(), e.getMessage());
            }
        }

        log.debug("Found {} components in slide '{}'", componentCount, slideNode.getTitle());
    }

    /**
     * Parse slide components (h6) under a slide node
     */
    private void parseSlideComponents(String content, ContentNode slideNode) {
        log.debug("Processing slide content (length: {}): \n{}", content.length(),
                content.length() > 300 ? content.substring(0, 300) + "..." : content);

        // Updated pattern with optional spaces and formatting
        Matcher matcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(content);
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
                log.warn("Unknown component type '{}' in slide: {}", componentTypeStr, slideNode.getTitle());
                continue;
            }

            try {
                // Create the slide component
                SlideComponent component = slideComponentService.createComponent(
                        slideNode.getId(),
                        componentType,
                        componentContent
                );
                log.info("Created {} component for slide: {}", componentType, slideNode.getTitle());
            } catch (Exception e) {
                log.error("Failed to create component {} for slide {}: {}",
                        componentTypeStr, slideNode.getTitle(), e.getMessage());
            }
        }
        log.debug("Found {} components in slide '{}'", componentCount, slideNode.getTitle());
    }

    /**
     * Parse slides that appear directly under a parent without proper section/topic containers.
     * This method creates implicit containers as needed and only processes slides that aren't
     * already covered by other parsing methods.
     */
    private void parseDirectSlides(String content, ContentNode parentNode) throws IOException, InterruptedException {
        // First identify regions that should be excluded (already processed by other parsers)
        Set<SlideRegion> processedRegions = new HashSet<>();

        // Find all section regions to exclude
        Matcher sectionMatcher = MarkdownPatterns.SECTION_PATTERN.matcher(content);
        while (sectionMatcher.find()) {
            int sectionStart = sectionMatcher.start();
            int sectionEnd = findEndPosition(content, sectionStart, MarkdownPatterns.SECTION_PATTERN);
            processedRegions.add(new SlideRegion(sectionStart, sectionEnd));
        }

        // Find all topic regions to exclude
        Matcher topicMatcher = MarkdownPatterns.TOPIC_PATTERN.matcher(content);
        while (topicMatcher.find()) {
            int topicStart = topicMatcher.start();
            int topicEnd = findEndPosition(content, topicStart, MarkdownPatterns.TOPIC_PATTERN);
            processedRegions.add(new SlideRegion(topicStart, topicEnd));
        }

        // Find all slides that are not within excluded regions
        Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(content);
        List<SlideData> directSlides = new ArrayList<>();

        while (slideMatcher.find()) {
            int slideStart = slideMatcher.start();

            // Skip if this slide is within a section or topic region
            boolean isAlreadyProcessed = false;
            for (SlideRegion region : processedRegions) {
                if (slideStart > region.start && slideStart < region.end) {
                    isAlreadyProcessed = true;
                    break;
                }
            }

            if (!isAlreadyProcessed) {
                // This is truly a direct slide - collect its data
                String seqNumber = slideMatcher.group(1).trim();
                String slideTitle = slideMatcher.group(2).trim();
                String slideContent = slideMatcher.group(3).trim();

                directSlides.add(new SlideData(slideTitle, seqNumber, slideContent));
            }
        }

        // If we found direct slides, create implicit containers and process them
        if (!directSlides.isEmpty()) {
            log.info("Found {} direct slides under {} that need implicit containers",
                    directSlides.size(), parentNode.getTitle());

            ContentNode actualParent = createImplicitContainers(parentNode);

            // Now process each direct slide with the appropriate parent
            for (SlideData slideData : directSlides) {
                processDirectSlide(actualParent, slideData);
            }
        }
    }

    /**
     * Create implicit section and topic containers as needed
     */
    private ContentNode createImplicitContainers(ContentNode parentNode) throws IOException, InterruptedException {
        ContentNode actualParent = parentNode;

        // If parent is Course or Lecture, we need implicit section
        if (parentNode.getNodeType() == ContentNode.NodeType.COURSE ||
                parentNode.getNodeType() == ContentNode.NodeType.LECTURE) {

            // Check if implicit section already exists
            Optional<ContentNode> existingSection = contentNodeRepository.findByParentIdOrderByDisplayOrder(parentNode.getId())
                    .stream()
                    .filter(node -> node.getNodeType() == ContentNode.NodeType.SECTION && "Implicit Section".equals(node.getTitle()))
                    .findFirst();

            if (existingSection.isPresent()) {
                actualParent = existingSection.get();
                log.debug("Using existing implicit section: {}", actualParent.getId());
            } else {
                ContentNode implicitSection = ContentNode.builder()
                        .nodeType(ContentNode.NodeType.SECTION)
                        .parent(parentNode)
                        .title("Implicit Section")
                        .displayOrder(5)  // Put before explicit sections
                        .path(parentNode.getPath() + "/Section/implicit")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                implicitSection = contentNodeService.createNode(implicitSection,
                        "Auto-generated section for slides without explicit section",
                        "Created implicit section");

                log.info("Created implicit section under {}", parentNode.getTitle());
                actualParent = implicitSection;
            }
        }

        // We always need an implicit topic if the parent isn't already a topic
        if (actualParent.getNodeType() != ContentNode.NodeType.TOPIC) {
            // Check if implicit topic already exists
            Optional<ContentNode> existingTopic = contentNodeRepository.findByParentIdOrderByDisplayOrder(actualParent.getId())
                    .stream()
                    .filter(node -> node.getNodeType() == ContentNode.NodeType.TOPIC && "Implicit Topic".equals(node.getTitle()))
                    .findFirst();

            if (existingTopic.isPresent()) {
                actualParent = existingTopic.get();
                log.debug("Using existing implicit topic: {}", actualParent.getId());
            } else {
                ContentNode implicitTopic = ContentNode.builder()
                        .nodeType(ContentNode.NodeType.TOPIC)
                        .parent(actualParent)
                        .title("Implicit Topic")
                        .displayOrder(5)  // Put before explicit topics
                        .path(actualParent.getPath() + "/Topic/implicit")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                implicitTopic = contentNodeService.createNode(implicitTopic,
                        "Auto-generated topic for slides without explicit topic",
                        "Created implicit topic");

                log.info("Created implicit topic under {}", actualParent.getTitle());
                actualParent = implicitTopic;
            }
        }

        return actualParent;
    }

    /**
     * Process a direct slide with the appropriate parent
     */
    private void processDirectSlide(ContentNode parent, SlideData slideData) throws IOException, InterruptedException {
        // Check if slide already exists
        boolean slideExists = false;
        int displayOrder = Integer.parseInt(slideData.seqNumber);

        List<ContentNode> existingSlides = contentNodeRepository.findByNodeType(ContentNode.NodeType.SLIDE);
        for (ContentNode node : existingSlides) {
            if (node.getTitle().equals(slideData.title) && node.getDisplayOrder() == displayOrder) {
                slideExists = true;
                log.info("Slide '{}' with sequence {} already exists (ID: {}), skipping",
                        slideData.title, slideData.seqNumber, node.getId());
                break;
            }
        }

        if (slideExists) {
            return;
        }

        // Create slide node
        try {
            ContentNode slideNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SLIDE)
                    .parent(parent)
                    .title(slideData.title)
                    .displayOrder(displayOrder)
                    .path(parent.getPath() + "/Slide/" + slideData.seqNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            slideNode = contentNodeService.createNode(slideNode,
                    slideData.content,
                    "Created direct slide: " + slideData.title);

            log.info("Created direct slide: {} under {}", slideData.title, parent.getTitle());

            // Process components
            processSlideComponents(slideData.content, slideNode);
        } catch (Exception e) {
            log.error("Error creating slide {}: {}", slideData.title, e.getMessage(), e);
        }
    }

    /**
     * Helper to find the end position of a section or topic
     */
    private int findEndPosition(String content, int startPos, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);

        // Set region to start after the current match
        matcher.region(startPos + 1, content.length());

        // If we find another match of the same pattern, that's our end
        if (matcher.find()) {
            return matcher.start();
        }

        // Otherwise, the end is the end of content
        return content.length();
    }

    private void parseTopicsDirectly(String content, ContentNode sectionNode) throws IOException, InterruptedException {
        Matcher topicMatcher = MarkdownPatterns.TOPIC_PATTERN.matcher(content);

        while (topicMatcher.find()) {
            // Process topics directly under this node (skipping expected parent levels)
            // The implementation is similar to parseTopics but without requiring specific parent types

            String topicTitle = topicMatcher.group(1).trim();

            // Find start/end positions as in other methods
            int topicStart = topicMatcher.start();
            // Find topic end logic...
            int topicEnd = content.length();

            // Save the current region position to restore later
            int originalRegionEnd = topicMatcher.regionEnd();

            // Look for the next topic after this one
            topicMatcher.region(topicMatcher.end(), content.length());
            if (topicMatcher.find()) {
                // If we find another topic, this topic ends where the next one starts
                topicEnd = topicMatcher.start();

                // Reset the matcher to continue from that position in the next iteration
                topicMatcher.region(topicEnd, content.length());
            } else {
                // If there are no more topics, reset the matcher to continue from where we left off
                topicMatcher = MarkdownPatterns.TOPIC_PATTERN.matcher(content);
                topicMatcher.region(topicStart + 1, originalRegionEnd);
            }

            // Extract topic content
            String topicContent = content.substring(topicStart, topicEnd);
            ContentNode topicNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.TOPIC)
                    .parent(sectionNode)
                    .title(topicTitle)
                    .displayOrder(10) // Default ordering
                    .path(sectionNode.getPath() + "/Topic/" + UUID.randomUUID().toString().substring(0, 8))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            topicNode = contentNodeService.createNode(topicNode, topicContent, "Created direct topic: " + topicTitle);

            // Parse slides under this topic
            parseSlides(topicContent, topicNode);
        }
    }

    /**
     * Helper class to store region information
     */
    private static class SlideRegion {
        final int start;
        final int end;

        SlideRegion(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Helper class to store slide data
     */
    private static class SlideData {
        final String title;
        final String seqNumber;
        final String content;

        SlideData(String title, String seqNumber, String content) {
            this.title = title;
            this.seqNumber = seqNumber;
            this.content = content;
        }
    }
}