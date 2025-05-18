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
     * @param node The content node to sync
     * @param branchName The Git branch to use (if null, a new branch will be created)
     * @return true if the file was successfully updated, false otherwise
     */
    public boolean syncNodeToFile(ContentNode node, String branchName) {
        try {
            // Determine which lecture file this content belongs to
            Path targetFile = determineTargetFile(node);

            // After determining targetFile
            log.debug("Target file for node '{}': {}", node.getTitle(), targetFile);

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

            // After reading file content
            log.debug("Existing file content length: {} bytes", fileContent.length());

            // Generate the new node content
            String nodeContent = generateNodeContent(node);

            // After generating node content
            log.debug("Generated content length: {} bytes", nodeContent.length());

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
     * Synchronize a transient node with the Git repository without database interaction
     */
    public boolean syncNodeToFileOnly(ContentNode transientNode, String branchName) {
        try {
            // Determine which lecture file this content belongs to
            Path targetFile = determineTargetFile(transientNode);
            if (targetFile == null) {
                log.error("Could not determine target file for node: {}", transientNode.getTitle());
                return false;
            }

            // Ensure the file exists
            if (!Files.exists(targetFile)) {
                log.error("Target file does not exist: {}", targetFile);
                return false;
            }

            // Read the current file content
            String fileContent = Files.readString(targetFile);

            // Generate content from the transient node
            String nodeContent = generateNodeContent(transientNode);
            if (nodeContent == null || nodeContent.isBlank()) {
                log.error("Failed to generate content for node: {}", transientNode.getTitle());
                return false;
            }

            // Insert the content at the appropriate location
            String updatedContent = insertContentAtAppropriateLocation(fileContent, transientNode, nodeContent);

            // Write the updated content back to the file
            Files.writeString(targetFile, updatedContent);
            log.info("Updated file {} with proposed content for {}", targetFile.getFileName(), transientNode.getTitle());

            return true;
        } catch (Exception e) {
            log.error("Failed to sync transient node to file: {}", e.getMessage(), e);
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
     * Generate content for a node based on its type.
     * Modified to handle transient nodes that might not have slide components
     */
    private String generateNodeContent(ContentNode node) {
        StringBuilder content = new StringBuilder();

        switch (node.getNodeType()) {
            case LECTURE:
                content.append("## ").append(node.getTitle()).append("\n\n");
                break;

            case SECTION:
                content.append("### ").append(node.getTitle()).append("\n\n");
                break;

            case TOPIC:
                content.append("#### ").append(node.getTitle()).append("\n\n");
                break;

            case SLIDE:
                // Format with display order as sequence number
                content.append("##### [seq:").append(node.getDisplayOrder() != null ?
                                String.format("%03d", node.getDisplayOrder()) : "000").append("] ")
                        .append(node.getTitle()).append("\n\n");

                // For transient nodes, we can't get components from the database
                // Instead, use the node's metadata or a placeholder
                content.append("<!-- Proposed slide content - will be populated after approval -->\n\n");
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
        log.debug("Determining insert location for node: {} (type: {})", node.getTitle(), node.getNodeType());

        // Simplify for debugging - instead of complex placement logic, let's use a direct approach
        switch (node.getNodeType()) {
            case LECTURE:
                // For lectures, simply add after the last lecture heading
                int lastLecturePos = fileContent.lastIndexOf("\n## ");
                if (lastLecturePos >= 0) {
                    // Find the end of this lecture section
                    int endOfLecture = fileContent.indexOf("\n## ", lastLecturePos + 1);
                    if (endOfLecture >= 0) {
                        return fileContent.substring(0, endOfLecture) + "\n\n" + nodeContent + fileContent.substring(endOfLecture);
                    } else {
                        // No more lectures - add to end
                        return fileContent + "\n\n" + nodeContent;
                    }
                } else {
                    // No lectures found - add after course title
                    int courseTitle = fileContent.indexOf("# ");
                    if (courseTitle >= 0) {
                        int endOfCourseTitle = fileContent.indexOf("\n", courseTitle);
                        if (endOfCourseTitle >= 0) {
                            return fileContent.substring(0, endOfCourseTitle + 1) + "\n" + nodeContent + fileContent.substring(endOfCourseTitle + 1);
                        }
                    }
                    return fileContent + "\n\n" + nodeContent;
                }

            case SECTION:
                // For sections, try to find a "### " section marker to attach to
                if (node.getParent() != null) {
                    // Try to find the parent lecture
                    String parentTitle = node.getParent().getTitle();
                    int parentPos = fileContent.indexOf("\n## " + parentTitle);

                    if (parentPos >= 0) {
                        // Found parent lecture, now find the first section after it
                        int lectureEnd = fileContent.indexOf("\n## ", parentPos + 1);
                        if (lectureEnd < 0) lectureEnd = fileContent.length();

                        // Insert right after the parent heading
                        int afterParentHeading = fileContent.indexOf("\n", parentPos) + 1;
                        return fileContent.substring(0, afterParentHeading) + "\n" + nodeContent + fileContent.substring(afterParentHeading);
                    }
                }

                // If we can't find the parent, insert after the last section
                int lastSectionPos = fileContent.lastIndexOf("\n### ");
                if (lastSectionPos >= 0) {
                    int endOfSection = fileContent.indexOf("\n### ", lastSectionPos + 1);
                    if (endOfSection >= 0) {
                        return fileContent.substring(0, endOfSection) + "\n\n" + nodeContent + fileContent.substring(endOfSection);
                    } else {
                        // No more sections - look for next lecture
                        endOfSection = fileContent.indexOf("\n## ", lastSectionPos);
                        if (endOfSection >= 0) {
                            return fileContent.substring(0, endOfSection) + "\n\n" + nodeContent + fileContent.substring(endOfSection);
                        } else {
                            // Add to end
                            return fileContent + "\n\n" + nodeContent;
                        }
                    }
                } else {
                    // No sections at all - add after first lecture
                    int firstLecture = fileContent.indexOf("\n## ");
                    if (firstLecture >= 0) {
                        int afterFirstLecture = fileContent.indexOf("\n", firstLecture + 1) + 1;
                        return fileContent.substring(0, afterFirstLecture) + "\n" + nodeContent + fileContent.substring(afterFirstLecture);
                    }
                    return fileContent + "\n\n" + nodeContent;
                }

                // Add similar simplified logic for TOPIC and SLIDE
            case TOPIC:
            case SLIDE:
            default:
                // For now, add a simpler approach - just add it somewhere visible for debugging
                // Let's put it after the first heading that matches the pattern for this node type
                String headingPattern = "";
                switch (node.getNodeType()) {
                    case TOPIC: headingPattern = "\n#### "; break;
                    case SLIDE: headingPattern = "\n##### "; break;
                    default: headingPattern = "\n### "; break;
                }

                int lastHeadingPos = fileContent.lastIndexOf(headingPattern);
                if (lastHeadingPos >= 0) {
                    int endOfHeading = fileContent.indexOf("\n", lastHeadingPos + 1);
                    if (endOfHeading >= 0) {
                        return fileContent.substring(0, endOfHeading + 1) + "\n" + nodeContent + fileContent.substring(endOfHeading + 1);
                    }
                }

                // Add debugging marker to see where it's being inserted
                log.warn("Using fallback insertion for node: {} (type: {})", node.getTitle(), node.getNodeType());
                nodeContent = "\n\n<!-- DEBUG: Content placement for " + node.getNodeType() + ": " + node.getTitle() + " -->\n" + nodeContent;

                return fileContent + nodeContent;
        }
    }
}