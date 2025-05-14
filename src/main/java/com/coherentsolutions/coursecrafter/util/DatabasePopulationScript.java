package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to populate the database with course content from markdown files.
 * This should only be run once to initialize the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Run after markdown files setup
public class DatabasePopulationScript implements CommandLineRunner {

    private final ContentNodeRepository contentNodeRepository;
    private final ContentVersionRepository contentVersionRepository;
    private final ContentNodeService contentNodeService;
    private final SlideComponentService slideComponentService;

    // Inject a check for whether import is enabled
    @Autowired(required = false)
    private Boolean databaseImportEnabled;

    // Default markdown files directory - can be overridden by properties
    @Value("${coursecrafter.import.folder:src/main/resources/course_content}")
    private String markdownFilesDir;

    // Filename pattern for lecture files - not included in MarkdownPatterns as it's specific to this class
    private static final Pattern LECTURE_FILENAME_PATTERN = Pattern.compile("Lecture (\\d+)[-\\s.]+(.*?)\\.md");

    // Numbered section pattern - extends the base SECTION_PATTERN
    private static final Pattern NUMBERED_SECTION_PATTERN = Pattern.compile("### (\\d+)\\. (.+?)\\n");

    // Numbered topic pattern - extends the base TOPIC_PATTERN
    private static final Pattern NUMBERED_TOPIC_PATTERN = Pattern.compile("#### (\\d+)\\.(\\d+)\\. (.+?)\\n");

    // First section marker for extracting introduction content
    private static final Pattern FIRST_SECTION_PATTERN = Pattern.compile("### 1\\.");

    @Override
    public void run(String... args) throws Exception {
        // Skip if import is disabled
        if (databaseImportEnabled != null && !databaseImportEnabled) {
            log.info("Database import is disabled. Skipping population script.");
            return;
        }

        log.info("Starting to populate database with course content from markdown files...");

        // Process each markdown file
        Path dirPath = Paths.get(markdownFilesDir);

        // Check if directory exists
        if (!Files.exists(dirPath)) {
            log.error("Markdown files directory does not exist: {}", markdownFilesDir);
            return;
        }

        // Create the parser
        MarkdownCourseParser parser = new MarkdownCourseParser(
                contentNodeRepository, contentNodeService, slideComponentService);

        // Process each .md file in the directory
        Files.list(dirPath)
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(path -> {
                    try {
                        parser.parseFile(path);
                    } catch (Exception e) {
                        log.error("Error processing file: {}", path, e);
                    }
                });

        log.info("Database population completed.");
    }

    private void populateDatabaseFromMarkdownFiles() throws IOException {
        // Create the root course node
        final ContentNode courseNode = ContentNode.builder()
                .nodeType(ContentNode.NodeType.COURSE)
                .title("AI Course")
                .description("A comprehensive course on AI tools and techniques")
                .nodeNumber("1")
                .displayOrder(1)
                .path("Course/AI")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        final ContentNode savedCourseNode = contentNodeRepository.save(courseNode);
        log.info("Created course root node: {}", savedCourseNode.getTitle());

        // Map to store all lecture nodes for reference when creating sections
        final Map<String, ContentNode> lectureNodes = new HashMap<>();

        // Process each markdown file
        Path dirPath = Paths.get(markdownFilesDir);

        // Check if directory exists
        if (!Files.exists(dirPath)) {
            log.error("Markdown files directory does not exist: {}", markdownFilesDir);
            return;
        }

        // Process files - using a non-lambda approach to avoid "effectively final" issues
        Files.list(dirPath)
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(path -> processMarkdownFileWrapper(path, savedCourseNode, lectureNodes));
    }

    // Wrapper method to handle exceptions and make lambda usage cleaner
    private void processMarkdownFileWrapper(Path path, ContentNode courseNode, Map<String, ContentNode> lectureNodes) {
        try {
            processMarkdownFile(path, courseNode, lectureNodes);
        } catch (Exception e) {
            log.error("Error processing file: {}", path, e);
        }
    }

    private void processMarkdownFile(Path filePath, ContentNode courseNode, Map<String, ContentNode> lectureNodes) throws IOException, InterruptedException {
        String fileName = filePath.getFileName().toString();
        String fileContent = Files.readString(filePath);

        // Extract lecture number and title from filename
        Matcher lectureMatcher = LECTURE_FILENAME_PATTERN.matcher(fileName);

        if (lectureMatcher.find()) {
            String lectureNumber = lectureMatcher.group(1);
            String lectureTitle = lectureMatcher.group(2).trim();

            // Create lecture node
            ContentNode lectureNode = ContentNode.builder()
                    .parent(courseNode)
                    .nodeType(ContentNode.NodeType.LECTURE)
                    .title(lectureTitle)
                    .description("Lecture " + lectureNumber + ": " + lectureTitle)
                    .nodeNumber(lectureNumber)
                    .displayOrder(Integer.parseInt(lectureNumber) * 10)
                    .path(courseNode.getPath() + "/Lecture/" + lectureNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            lectureNode = contentNodeService.createNode(lectureNode, getIntroductionContent(fileContent),
                    "Initial import of Lecture " + lectureNumber);

            log.info("Created lecture node: {}", lectureNode.getTitle());

            // Store for reference when creating sections
            lectureNodes.put(lectureNumber, lectureNode);

            // Process sections within the lecture
            processSections(fileContent, lectureNode, lectureNumber);
        } else {
            log.warn("File name does not match the expected pattern: {}", fileName);
        }
    }

    private void processSections(String content, ContentNode lectureNode, String lectureNumber) throws IOException, InterruptedException {
        // Find all sections with numbered headers (### 1. Section Title)
        Matcher sectionMatcher = NUMBERED_SECTION_PATTERN.matcher(content);

        int sectionOrder = 1;

        while (sectionMatcher.find()) {
            String sectionNumberRaw = sectionMatcher.group(1);
            String sectionTitle = sectionMatcher.group(2).trim();

            // Calculate where the section content starts
            int sectionStart = sectionMatcher.end();
            int sectionEnd;

            // Find the next section or end of file
            Matcher nextSection = NUMBERED_SECTION_PATTERN.matcher(content.substring(sectionStart));
            if (nextSection.find()) {
                sectionEnd = sectionStart + nextSection.start() - 1;
            } else {
                sectionEnd = content.length();
            }

            // Extract section content
            String sectionContent = content.substring(sectionStart, sectionEnd).trim();

            // Create section node
            ContentNode sectionNode = ContentNode.builder()
                    .parent(lectureNode)
                    .nodeType(ContentNode.NodeType.SECTION)
                    .title(sectionTitle)
                    .description("Section " + sectionNumberRaw + " of Lecture " + lectureNumber)
                    .nodeNumber(lectureNumber + "." + sectionNumberRaw)
                    .displayOrder(sectionOrder * 10)
                    .path(lectureNode.getPath() + "/Section/" + sectionNumberRaw)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sectionNode = contentNodeService.createNode(sectionNode, sectionContent,
                    "Initial import of Section " + sectionNumberRaw + " in Lecture " + lectureNumber);

            log.info("Created section node: {}", sectionNode.getTitle());

            // Process subsections and slides
            processSubsections(sectionContent, sectionNode, lectureNumber, sectionNumberRaw);

            sectionOrder++;
        }
    }

    private void processSubsections(String content, ContentNode sectionNode, String lectureNumber, String sectionNumber) throws IOException, InterruptedException {
        // Find all subsections with hierarchical numbering (#### 1.1. Topic Title)
        Matcher subsectionMatcher = NUMBERED_TOPIC_PATTERN.matcher(content);

        int subsectionOrder = 1;

        while (subsectionMatcher.find()) {
            String majorNumber = subsectionMatcher.group(1);
            String minorNumber = subsectionMatcher.group(2);
            String subsectionTitle = subsectionMatcher.group(3).trim();

            // Only process if major number matches section number
            if (!majorNumber.equals(sectionNumber)) {
                continue;
            }

            // Calculate where the subsection content starts
            int subsectionStart = subsectionMatcher.end();
            int subsectionEnd;

            // Find the next subsection or end of section
            Matcher nextSubsection = NUMBERED_TOPIC_PATTERN.matcher(content.substring(subsectionStart));
            if (nextSubsection.find()) {
                subsectionEnd = subsectionStart + nextSubsection.start() - 1;
            } else {
                subsectionEnd = content.length();
            }

            // Extract subsection content
            String subsectionContent = content.substring(subsectionStart, subsectionEnd).trim();

            // Create topic node for subsection
            ContentNode topicNode = ContentNode.builder()
                    .parent(sectionNode)
                    .nodeType(ContentNode.NodeType.TOPIC)
                    .title(subsectionTitle)
                    .description("Topic " + majorNumber + "." + minorNumber + " of Lecture " + lectureNumber)
                    .nodeNumber(lectureNumber + "." + majorNumber + "." + minorNumber)
                    .displayOrder(subsectionOrder * 10)
                    .path(sectionNode.getPath() + "/Topic/" + minorNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            topicNode = contentNodeService.createNode(topicNode, subsectionContent,
                    "Initial import of Topic " + majorNumber + "." + minorNumber + " in Lecture " + lectureNumber);

            log.info("Created topic node: {}", topicNode.getTitle());

            // Process slides - using the centralized pattern
            processSlides(subsectionContent, topicNode, lectureNumber, majorNumber, minorNumber);

            subsectionOrder++;
        }
    }

    private void processSlides(String content, ContentNode topicNode, String lectureNumber, String sectionNumber, String topicNumber) throws IOException, InterruptedException {
        // Use centralized SLIDE_PATTERN for slide detection
        Matcher slideMatcher = MarkdownPatterns.SLIDE_PATTERN.matcher(content);

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
                displayOrder = 100; // Default value if parsing fails
            }

            // Create slide node with sequence-based ordering
            ContentNode slideNode = ContentNode.builder()
                    .parent(topicNode)
                    .nodeType(ContentNode.NodeType.SLIDE)
                    .title(slideTitle)
                    .description("Slide " + seqNumber + " for Topic " + sectionNumber + "." + topicNumber)
                    .nodeNumber(lectureNumber + "." + sectionNumber + "." + topicNumber + "." + seqNumber)
                    .displayOrder(displayOrder)
                    .path(topicNode.getPath() + "/Slide/" + seqNumber)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            slideNode = contentNodeService.createNode(slideNode, slideContent,
                    "Created slide: " + slideTitle + " with sequence " + seqNumber);

            log.info("Created slide: {} with sequence {}", slideTitle, seqNumber);

            // Process components within this slide
            processSlideComponents(slideContent, slideNode);
        }
    }

    /**
     * Process components within a slide
     */
    private void processSlideComponents(String slideContent, ContentNode slideNode) {
        // Use centralized COMPONENT_PATTERN for component detection
        Matcher componentMatcher = MarkdownPatterns.COMPONENT_PATTERN.matcher(slideContent);

        // Track how many components we find for debugging
        int componentCount = 0;

        while (componentMatcher.find()) {
            componentCount++;

            String componentTypeStr = componentMatcher.group(1).trim();
            String componentContent = componentMatcher.group(2).trim();

            // Convert string to enum
            SlideComponent.ComponentType componentType;
            try {
                componentType = SlideComponent.ComponentType.valueOf(componentTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown component type: {} in slide: {}", componentTypeStr, slideNode.getTitle());
                continue;
            }

            try {
                // Create component using the service
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

    private String getIntroductionContent(String content) {
        // Extract the introduction part - everything before the first section
        Matcher matcher = FIRST_SECTION_PATTERN.matcher(content);

        if (matcher.find()) {
            return content.substring(0, matcher.start()).trim();
        }

        // If no sections found, return the first 500 characters or the whole content if shorter
        return content.length() > 500 ? content.substring(0, 500) : content;
    }
}