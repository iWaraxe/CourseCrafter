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

    // Constants for parsing
    private static final Pattern COURSE_PATTERN = Pattern.compile("^# ([^\\n]+)", Pattern.MULTILINE);
    private static final Pattern LECTURE_PATTERN = Pattern.compile("^## ([^\\n]+)", Pattern.MULTILINE);
    private static final Pattern SECTION_PATTERN = Pattern.compile("^### ([^\\n]+)", Pattern.MULTILINE);
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^#### ([^\\n]+)", Pattern.MULTILINE);
    private static final Pattern SLIDE_PATTERN = Pattern.compile("^##### \\[seq:(\\d+)\\] ([^\\n]+)([\\s\\S]*?)(?=^---$|^##### |$)",
            Pattern.MULTILINE);
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("^###### ([A-Z]+)\\s*([\\s\\S]*?)(?=^###### |^---$|^##### |$)",
            Pattern.MULTILINE);

    /**
     * Parse a markdown file and create the entire course hierarchy
     */
    public void parseFile(Path filePath) throws IOException, InterruptedException {
        log.info("Parsing markdown file: {}", filePath.getFileName());
        String content = Files.readString(filePath);

        // Parse course
        ContentNode courseNode = parseCourse(content);
        if (courseNode == null) {
            log.error("No course found in file: {}", filePath);
            return;
        }

        // Parse lectures under the course
        parseLectures(content, courseNode);
    }

    /**
     * Parse the course level (h1)
     */
    private ContentNode parseCourse(String content) throws IOException, InterruptedException {
        Matcher courseMatcher = COURSE_PATTERN.matcher(content);
        if (!courseMatcher.find()) {
            return null;
        }

        String courseTitle = courseMatcher.group(1).trim();
        log.info("Creating course: {}", courseTitle);

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
        Matcher lectureMatcher = LECTURE_PATTERN.matcher(content);
        int lectureOrder = 10; // Start at 10 to leave room

        while (lectureMatcher.find()) {
            String lectureTitle = lectureMatcher.group(1).trim();

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
                lectureMatcher = LECTURE_PATTERN.matcher(content);
                lectureMatcher.region(lectureStart + 1, content.length());
            }

            // Extract lecture content
            String lectureContent = content.substring(lectureStart, lectureEnd);

            // Create lecture node
            ContentNode lectureNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.LECTURE)
                    .parent(courseNode)
                    .title(lectureTitle)
                    .displayOrder(lectureOrder)
                    .path(courseNode.getPath() + "/Lecture/" + lectureOrder)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            lectureNode = contentNodeService.createNode(lectureNode,
                    lectureContent,
                    "Created lecture: " + lectureTitle);
            log.info("Created lecture: {} with order {}", lectureTitle, lectureOrder);

            // Parse sections within the lecture
            parseSections(lectureContent, lectureNode);

            // Also check for slides directly under the lecture
            parseSlides(lectureContent, lectureNode);

            lectureOrder += 10;
        }
    }

    /**
     * Parse sections (h3) under a lecture
     */
    private void parseSections(String content, ContentNode lectureNode) throws IOException, InterruptedException {
        Matcher sectionMatcher = SECTION_PATTERN.matcher(content);
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
                sectionMatcher = SECTION_PATTERN.matcher(content);
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
        Matcher topicMatcher = TOPIC_PATTERN.matcher(content);
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
                topicMatcher = TOPIC_PATTERN.matcher(content);
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
        Matcher slideMatcher = SLIDE_PATTERN.matcher(content);

        while (slideMatcher.find()) {
            String seqNumber = slideMatcher.group(1).trim();
            String slideTitle = slideMatcher.group(2).trim();
            String slideContent = slideMatcher.group(3).trim();

            // Convert sequence number to display order
            int displayOrder;
            try {
                displayOrder = Integer.parseInt(seqNumber);
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number: {} for slide: {}, using default", seqNumber, slideTitle);
                displayOrder = 1000; // Default high value if parsing fails
            }

            // Create slide node with sequence-based display order
            ContentNode slideNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.SLIDE)
                    .parent(parentNode)
                    .title(slideTitle)
                    .displayOrder(displayOrder)
                    .path(parentNode.getPath() + "/Slide/" + seqNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            slideNode = contentNodeService.createNode(slideNode,
                    slideContent,
                    "Created slide: " + slideTitle);
            log.info("Created slide: {} with sequence {}", slideTitle, seqNumber);

            // Parse components for this slide
            parseSlideComponents(slideContent, slideNode);
        }
    }

    /**
     * Parse slide components (h6) under a slide node
     */
    private void parseSlideComponents(String content, ContentNode slideNode) {
        // Define pattern to match component sections (level 6 headers)
        Pattern componentPattern = Pattern.compile("^#{6}\\s+(SCRIPT|VISUAL|NOTES|DEMONSTRATION)\\s*$(.*?)(?=^#{6}|^-{3,}|$)",
                Pattern.DOTALL | Pattern.MULTILINE);

        Matcher matcher = componentPattern.matcher(content);

        // Process each component found
        while (matcher.find()) {
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
    }
}