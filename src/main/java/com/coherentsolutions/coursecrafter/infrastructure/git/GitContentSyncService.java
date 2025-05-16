package com.coherentsolutions.coursecrafter.infrastructure.git;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitContentSyncService {

    private final ContentNodeRepository contentNodeRepository;
    private final SlideComponentRepository slideComponentRepository;

    @Value("${git.repo.root}")
    private String repoRoot;

    // Cache of lecture nodes to avoid repeated database lookups
    private Map<Long, ContentNode> lectureCache = new HashMap<>();

    /**
     * Synchronize a database node with its corresponding file in the Git repository
     */
    public boolean syncNodeToFile(ContentNode node) {
        try {
            // Find which lecture this node belongs to
            ContentNode lectureNode = findLectureForNode(node);
            if (lectureNode == null) {
                log.error("Could not find lecture for node: {}", node.getTitle());
                return false;
            }

            // Get the markdown file path for this lecture
            Path markdownFile = getMarkdownFileForLecture(lectureNode);
            if (markdownFile == null) {
                log.error("Could not find markdown file for lecture: {}", lectureNode.getTitle());
                return false;
            }

            // Read the current file content
            String fileContent = Files.exists(markdownFile)
                    ? Files.readString(markdownFile)
                    : "# " + lectureNode.getTitle() + "\n\n";

            // Update file content based on node type
            String updatedContent = updateFileContentForNode(fileContent, node);

            // Write the updated content back to the file
            Files.writeString(markdownFile, updatedContent);
            log.info("Updated markdown file for node: {} at path: {}", node.getTitle(), markdownFile);

            return true;
        } catch (Exception e) {
            log.error("Failed to sync node to file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Find the lecture that this node belongs to by traversing up the hierarchy
     */
    private ContentNode findLectureForNode(ContentNode node) {
        // If this is already a lecture, return it
        if (node.getNodeType() == ContentNode.NodeType.LECTURE) {
            return node;
        }

        // If we've cached this node's lecture, return from cache
        if (lectureCache.containsKey(node.getId())) {
            return lectureCache.get(node.getId());
        }

        // Traverse up the hierarchy until we find a lecture
        ContentNode current = node;
        while (current != null && current.getNodeType() != ContentNode.NodeType.LECTURE) {
            current = current.getParent();
        }

        // Cache the result to avoid future lookups
        if (current != null) {
            lectureCache.put(node.getId(), current);
        }

        return current;
    }

    /**
     * Get the markdown file path for a lecture
     */
    private Path getMarkdownFileForLecture(ContentNode lectureNode) {
        // Extract lecture number if available (e.g., from node title or number)
        String lectureNumber = extractLectureNumber(lectureNode);

        // First try to find an exact match for the lecture
        Path repoPath = Paths.get(repoRoot);
        try {
            // Look for files matching Lecture X pattern
            Optional<Path> matchingFile = Files.list(repoPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        // Match either by lecture number or by title
                        return (lectureNumber != null && filename.contains("Lecture " + lectureNumber)) ||
                                filename.contains(lectureNode.getTitle());
                    })
                    .findFirst();

            if (matchingFile.isPresent()) {
                return matchingFile.get();
            }

            // If no match found, create a new file
            String newFilename = lectureNumber != null
                    ? "Lecture " + lectureNumber + "- " + lectureNode.getTitle() + ".md"
                    : "Lecture- " + lectureNode.getTitle() + ".md";

            Path newFile = repoPath.resolve(newFilename);
            if (!Files.exists(newFile)) {
                Files.createFile(newFile);
                log.info("Created new markdown file for lecture: {}", newFile);
            }
            return newFile;

        } catch (IOException e) {
            log.error("Failed to find or create markdown file for lecture: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract lecture number from the node
     */
    private String extractLectureNumber(ContentNode lectureNode) {
        // First try to get from nodeNumber
        if (lectureNode.getNodeNumber() != null && !lectureNode.getNodeNumber().isEmpty()) {
            return lectureNode.getNodeNumber();
        }

        // Then try to extract from title (e.g., "Lecture 1: Introduction")
        Pattern pattern = Pattern.compile("Lecture (\\d+)");
        Matcher matcher = pattern.matcher(lectureNode.getTitle());
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Update the file content with the node's content
     */
    private String updateFileContentForNode(String fileContent, ContentNode node) {
        switch (node.getNodeType()) {
            case SECTION:
                return updateContentForSection(fileContent, node);
            case TOPIC:
                return updateContentForTopic(fileContent, node);
            case SLIDE:
                return updateContentForSlide(fileContent, node);
            default:
                log.warn("Unsupported node type for file update: {}", node.getNodeType());
                return fileContent;
        }
    }

    /**
     * Update file content for a section node
     */
    private String updateContentForSection(String fileContent, ContentNode sectionNode) {
        // Get the section content from the latest version
        String sectionContent = getLatestNodeContent(sectionNode);

        // Create a new section header
        String sectionHeader = "### " + sectionNode.getTitle() + "\n\n";

        // Look for the next section to insert before, or append at the end
        Pattern sectionPattern = Pattern.compile("### .+");
        Matcher matcher = sectionPattern.matcher(fileContent);

        List<Integer> sectionPositions = new ArrayList<>();
        while (matcher.find()) {
            sectionPositions.add(matcher.start());
        }

        // Decide where to insert the new section
        if (sectionPositions.isEmpty()) {
            // If no sections found, append to the end
            return fileContent + "\n\n" + sectionHeader + sectionContent;
        } else {
            // Insert after the course/lecture header but before any other sections
            int insertPosition = sectionPositions.get(0);
            return fileContent.substring(0, insertPosition) +
                    sectionHeader + sectionContent + "\n\n" +
                    fileContent.substring(insertPosition);
        }
    }

    /**
     * Update file content for a topic node
     */
    private String updateContentForTopic(String fileContent, ContentNode topicNode) {
        // Get the topic content from the latest version
        String topicContent = getLatestNodeContent(topicNode);

        // Find the parent section to add this topic under
        ContentNode parentSection = topicNode.getParent();
        if (parentSection == null || parentSection.getNodeType() != ContentNode.NodeType.SECTION) {
            log.warn("Topic node does not have a section parent, adding at the end: {}", topicNode.getTitle());
            return fileContent + "\n\n#### " + topicNode.getTitle() + "\n\n" + topicContent;
        }

        // Try to find the section in the file
        Pattern sectionPattern = Pattern.compile("### " + Pattern.quote(parentSection.getTitle()) + "\\s*\\n");
        Matcher sectionMatcher = sectionPattern.matcher(fileContent);

        if (!sectionMatcher.find()) {
            log.warn("Parent section not found in file, adding topic at the end: {}", parentSection.getTitle());
            return fileContent + "\n\n#### " + topicNode.getTitle() + "\n\n" + topicContent;
        }

        // Find where the section content ends (next section or EOF)
        int sectionStart = sectionMatcher.end();
        int sectionEnd = fileContent.length();

        Matcher nextSectionMatcher = Pattern.compile("\\n### ").matcher(fileContent.substring(sectionStart));
        if (nextSectionMatcher.find()) {
            sectionEnd = sectionStart + nextSectionMatcher.start();
        }

        // Insert the topic into the section
        String topicHeader = "#### " + topicNode.getTitle() + "\n\n";
        return fileContent.substring(0, sectionEnd) +
                "\n\n" + topicHeader + topicContent +
                fileContent.substring(sectionEnd);
    }

    /**
     * Update file content for a slide node
     */
    private String updateContentForSlide(String fileContent, ContentNode slideNode) {
        // Get the slide components
        List<SlideComponent> components = slideComponentRepository.findBySlideNodeIdOrderByDisplayOrder(slideNode.getId());

        // Build the slide content with its components
        StringBuilder slideContent = new StringBuilder();
        slideContent.append("##### [seq:").append(slideNode.getDisplayOrder()).append("] ").append(slideNode.getTitle()).append("\n\n");

        // Add each component
        for (SlideComponent component : components) {
            slideContent.append("###### ").append(component.getComponentType()).append("\n");
            slideContent.append(component.getContent()).append("\n\n");
        }

        // If no components were added, add the slide's own content
        if (components.isEmpty()) {
            String content = getLatestNodeContent(slideNode);
            if (content != null && !content.isBlank()) {
                slideContent.append(content).append("\n\n");
            }
        }

        // Find the parent topic/section to add this slide under
        ContentNode parent = slideNode.getParent();
        if (parent == null) {
            log.warn("Slide node does not have a parent, adding at the end: {}", slideNode.getTitle());
            return fileContent + "\n\n" + slideContent;
        }

        // Build the pattern to match the parent heading
        String headerLevel;
        switch (parent.getNodeType()) {
            case TOPIC:
                headerLevel = "####";
                break;
            case SECTION:
                headerLevel = "###";
                break;
            default:
                headerLevel = "##";  // Lecture
        }

        Pattern parentPattern = Pattern.compile(headerLevel + " " + Pattern.quote(parent.getTitle()) + "\\s*\\n");
        Matcher parentMatcher = parentPattern.matcher(fileContent);

        if (!parentMatcher.find()) {
            log.warn("Parent not found in file, adding slide at the end: {}", parent.getTitle());
            return fileContent + "\n\n" + slideContent;
        }

        // Find where the parent content ends (next section at same level or EOF)
        int parentStart = parentMatcher.end();
        int parentEnd = fileContent.length();

        Matcher nextHeadingMatcher = Pattern.compile("\\n" + headerLevel + " ").matcher(fileContent.substring(parentStart));
        if (nextHeadingMatcher.find()) {
            parentEnd = parentStart + nextHeadingMatcher.start();
        }

        // Insert the slide into the parent section
        return fileContent.substring(0, parentEnd) +
                "\n\n" + slideContent +
                fileContent.substring(parentEnd);
    }

    /**
     * Get the latest content for a node from its versions
     */
    private String getLatestNodeContent(ContentNode node) {
        if (node.getVersions() == null || node.getVersions().isEmpty()) {
            return "";
        }

        return node.getVersions().stream()
                .max(Comparator.comparing(v -> v.getVersionNumber()))
                .map(v -> v.getContent())
                .orElse("");
    }
}