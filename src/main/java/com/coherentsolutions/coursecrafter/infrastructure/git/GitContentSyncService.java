package com.coherentsolutions.coursecrafter.infrastructure.git;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class GitContentSyncService {

    private final SlideComponentRepository slideComponentRepository;

    @Value("${git.repo.root}")
    private String repoRoot;

    // The fixed list of lecture files we're targeting
    private static final String[] LECTURE_FILES = {
            "Lecture 1- Introduction to AI and Current Developments.md",
            "Lecture 2. Practical AI Application - Text, Code and Visuals.md",
            "Lecture 3- Advanced Features and User Interfaces of Leading AI Tools.md",
            "Lecture 4. Expanding AI Horizons.md"
    };

    /**
     * Synchronize a database node with its corresponding file in the Git repository
     */
    public boolean syncNodeToFile(ContentNode node) {
        try {
            // Determine which lecture file this content belongs to
            Path targetFile = determineTargetFile(node);
            if (targetFile == null) {
                log.error("Could not determine target file for node: {}", node.getTitle());
                return false;
            }

            // Ensure the file exists
            if (!Files.exists(targetFile)) {
                log.error("Target file does not exist: {}", targetFile);
                return false;
            }

            // Read the current file content - ALWAYS read from disk to get the latest content
            String fileContent = Files.readString(targetFile);

            // Generate the new node content
            String nodeContent = generateNodeContent(node);
            if (nodeContent == null || nodeContent.isBlank()) {
                log.error("Failed to generate content for node: {}", node.getTitle());
                return false;
            }

            // Insert the content at the appropriate location
            String updatedContent = insertContentAtAppropriateLocation(fileContent, node, nodeContent);

            // Write the updated content back to the file
            Files.writeString(targetFile, updatedContent);
            log.info("Updated file {} with new content for {}", targetFile.getFileName(), node.getTitle());

            return true;
        } catch (Exception e) {
            log.error("Failed to sync node to file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Determine which lecture file this content should be added to.
     * This uses heuristics based on the content type and title.
     */
    private Path determineTargetFile(ContentNode node) {
        // Default to the first lecture file for most content
        String fileName = LECTURE_FILES[0];

        // Content about practical applications goes in Lecture 2
        if (isAboutPracticalApplications(node)) {
            fileName = LECTURE_FILES[1];
        }
        // Advanced features go in Lecture 3
        else if (isAboutAdvancedFeatures(node)) {
            fileName = LECTURE_FILES[2];
        }
        // Future-looking content goes in Lecture 4
        else if (isAboutFutureExpansion(node)) {
            fileName = LECTURE_FILES[3];
        }

        return Paths.get(repoRoot, fileName);
    }

    /**
     * Check if the content is about practical applications (Lecture 2)
     */
    private boolean isAboutPracticalApplications(ContentNode node) {
        String title = node.getTitle().toLowerCase();
        return title.contains("practical") ||
                title.contains("application") ||
                title.contains("technique") ||
                title.contains("prompt example") ||
                title.contains("demonstration") ||
                title.contains("showcase") ||
                title.contains("practice");
    }

    /**
     * Check if the content is about advanced features (Lecture 3)
     */
    private boolean isAboutAdvancedFeatures(ContentNode node) {
        String title = node.getTitle().toLowerCase();
        return title.contains("advanced") ||
                title.contains("feature") ||
                title.contains("interface") ||
                title.contains("tools") ||
                title.contains("business implication");
    }

    /**
     * Check if the content is about future expansion (Lecture 4)
     */
    private boolean isAboutFutureExpansion(ContentNode node) {
        String title = node.getTitle().toLowerCase();
        return title.contains("future") ||
                title.contains("expand") ||
                title.contains("horizon") ||
                title.contains("resources") ||
                title.contains("learn more");
    }

    /**
     * Generate content for a node based on its type
     */
    private String generateNodeContent(ContentNode node) {
        StringBuilder content = new StringBuilder();

        switch (node.getNodeType()) {
            case LECTURE:
                content.append("## ").append(node.getTitle()).append("\n\n");
                content.append(getLatestNodeContent(node)).append("\n\n");
                break;

            case SECTION:
                content.append("### ").append(node.getTitle()).append("\n\n");
                content.append(getLatestNodeContent(node)).append("\n\n");
                break;

            case TOPIC:
                content.append("#### ").append(node.getTitle()).append("\n\n");
                content.append(getLatestNodeContent(node)).append("\n\n");
                break;

            case SLIDE:
                // Format with display order as sequence number
                content.append("##### [seq:").append(node.getDisplayOrder() != null ?
                                String.format("%03d", node.getDisplayOrder()) : "000").append("] ")
                        .append(node.getTitle()).append("\n\n");

                // Add slide components if they exist
                List<SlideComponent> components = slideComponentRepository.findBySlideNodeIdOrderByDisplayOrder(node.getId());

                if (!components.isEmpty()) {
                    for (SlideComponent component : components) {
                        content.append("###### ").append(component.getComponentType()).append("\n");
                        content.append(component.getContent()).append("\n\n");
                    }
                } else {
                    // Add slide content if no components
                    content.append(getLatestNodeContent(node)).append("\n\n");
                }
                break;

            default:
                log.warn("Unsupported node type: {}", node.getNodeType());
                return null;
        }

        return content.toString();
    }

    /**
     * Insert content at an appropriate location in the file based on node type and content
     */
    private String insertContentAtAppropriateLocation(String fileContent, ContentNode node, String nodeContent) {
        // Default insert position is at the end of the file
        int insertPosition = fileContent.length();

        switch (node.getNodeType()) {
            case LECTURE:
                // Insert after the last lecture
                Pattern lecturePattern = Pattern.compile("^## ", Pattern.MULTILINE);
                Matcher lectureMatcher = lecturePattern.matcher(fileContent);

                while (lectureMatcher.find()) {
                    insertPosition = lectureMatcher.start();
                }

                // If we found a match, go to the end of that section
                if (insertPosition < fileContent.length()) {
                    int nextLecturePos = fileContent.indexOf("\n## ", insertPosition + 1);
                    if (nextLecturePos > -1) {
                        insertPosition = nextLecturePos;
                    } else {
                        insertPosition = fileContent.length();
                    }
                }
                break;

            case SECTION:
                // Find the last section of the appropriate lecture
                Pattern sectionPattern = Pattern.compile("^### ", Pattern.MULTILINE);
                Matcher sectionMatcher = sectionPattern.matcher(fileContent);

                while (sectionMatcher.find()) {
                    insertPosition = sectionMatcher.start();
                }

                // If we found a match, go to the end of that section
                if (insertPosition < fileContent.length()) {
                    int nextSectionPos = fileContent.indexOf("\n### ", insertPosition + 1);
                    if (nextSectionPos > -1) {
                        insertPosition = nextSectionPos;
                    } else {
                        insertPosition = fileContent.length();
                    }
                }
                break;

            case TOPIC:
                // Insert after the last topic
                Pattern topicPattern = Pattern.compile("^#### ", Pattern.MULTILINE);
                Matcher topicMatcher = topicPattern.matcher(fileContent);

                while (topicMatcher.find()) {
                    insertPosition = topicMatcher.start();
                }

                // If we found a match, go to the end of that topic
                if (insertPosition < fileContent.length()) {
                    int nextTopicPos = fileContent.indexOf("\n#### ", insertPosition + 1);
                    if (nextTopicPos > -1) {
                        insertPosition = nextTopicPos;
                    } else {
                        insertPosition = fileContent.length();
                    }
                }
                break;

            case SLIDE:
                // Find the appropriate section to add this slide to
                // For simplicity, we'll just add to the end of the file
                // In a real implementation, you would find the right section
                break;
        }

        // Insert the content at the determined position
        if (insertPosition == fileContent.length()) {
            // Ensure we have line breaks before adding content
            if (!fileContent.endsWith("\n\n")) {
                if (fileContent.endsWith("\n")) {
                    return fileContent + "\n" + nodeContent;
                } else {
                    return fileContent + "\n\n" + nodeContent;
                }
            } else {
                return fileContent + nodeContent;
            }
        } else {
            // Insert in the middle with proper spacing
            return fileContent.substring(0, insertPosition) + "\n\n" +
                    nodeContent + fileContent.substring(insertPosition);
        }
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