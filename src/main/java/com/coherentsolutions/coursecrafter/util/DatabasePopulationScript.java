package com.coherentsolutions.coursecrafter.util;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
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
        Pattern lecturePattern = Pattern.compile("Lecture (\\d+)[-\\s.]+(.*?)\\.md");
        Matcher lectureMatcher = lecturePattern.matcher(fileName);

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
        // Find all sections (level 2 headers in markdown)
        Pattern sectionPattern = Pattern.compile("### (\\d+)\\. (.+?)\\n");
        Matcher sectionMatcher = sectionPattern.matcher(content);

        int sectionOrder = 1;

        while (sectionMatcher.find()) {
            String sectionNumberRaw = sectionMatcher.group(1);
            String sectionTitle = sectionMatcher.group(2).trim();

            // Calculate where the section content starts
            int sectionStart = sectionMatcher.end();
            int sectionEnd;

            // Find the next section or end of file
            Matcher nextSection = sectionPattern.matcher(content.substring(sectionStart));
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
        // Find all subsections (level 3 headers in markdown)
        Pattern subsectionPattern = Pattern.compile("#### (\\d+)\\.(\\d+)\\. (.+?)\\n");
        Matcher subsectionMatcher = subsectionPattern.matcher(content);

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
            Matcher nextSubsection = subsectionPattern.matcher(content.substring(subsectionStart));
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

            // Process slides - looking for components like "Script", "Slide", "Demo"
            processSlides(subsectionContent, topicNode, lectureNumber, majorNumber, minorNumber);

            subsectionOrder++;
        }
    }

    private void processSlides(String content, ContentNode topicNode, String lectureNumber, String sectionNumber, String topicNumber) throws IOException, InterruptedException {
        // Extract slides and components - look for script, slide, demo components
        String[] slideTypes = {"Script", "Slide", "Demo", "Demonstration", "Instructions"};

        for (String slideType : slideTypes) {
            Pattern pattern = Pattern.compile("\\*\\*" + slideType + "(?::|\\*\\*)(.+?)(?:\\*\\*|$)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);

            int slideOrder = 1;

            while (matcher.find()) {
                String slideContent = matcher.group(1).trim();

                // Create a slide for each component
                ContentNode slideNode = ContentNode.builder()
                        .parent(topicNode)
                        .nodeType(ContentNode.NodeType.SLIDE)
                        .title(slideType + " " + slideOrder)
                        .description(slideType + " for Topic " + sectionNumber + "." + topicNumber)
                        .nodeNumber(lectureNumber + "." + sectionNumber + "." + topicNumber + "." + slideOrder)
                        .displayOrder(slideOrder * 10)
                        .path(topicNode.getPath() + "/Slide/" + slideType.toLowerCase() + "-" + slideOrder)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                slideNode = contentNodeService.createNode(slideNode, slideContent,
                        "Initial import of " + slideType + " " + slideOrder + " in Topic " + sectionNumber + "." + topicNumber);

                log.info("Created slide node for {}: {}", slideType, slideNode.getTitle());

                slideOrder++;
            }
        }
    }

    private String getIntroductionContent(String content) {
        // Extract the introduction part - everything before the first section
        Pattern sectionPattern = Pattern.compile("### 1\\.");
        Matcher matcher = sectionPattern.matcher(content);

        if (matcher.find()) {
            return content.substring(0, matcher.start()).trim();
        }

        // If no sections found, return the first 500 characters or the whole content if shorter
        return content.length() > 500 ? content.substring(0, 500) : content;
    }
}