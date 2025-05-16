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
        log.debug("Attempting to insert content for node: {}, type: {}", node.getTitle(), node.getNodeType());

        // Default insert position is at the end of the file
        int insertPosition = fileContent.length();

        switch (node.getNodeType()) {
            case LECTURE:
                // For lectures, insert after the "## Lecture X:" header that matches the node title
                // or at the end of the file if not found
                Pattern lecturePattern = Pattern.compile("^## " + Pattern.quote(node.getTitle()), Pattern.MULTILINE);
                Matcher lectureMatcher = lecturePattern.matcher(fileContent);

                if (lectureMatcher.find()) {
                    // Found the lecture - now find the next section or end of file
                    int lectureStart = lectureMatcher.start();
                    int nextLecturePos = fileContent.indexOf("\n## ", lectureStart + 1);

                    if (nextLecturePos > -1) {
                        // Insert before the next lecture
                        insertPosition = nextLecturePos;
                        log.debug("Inserting lecture content before next lecture at position {}", insertPosition);
                    } else {
                        // No next lecture - insert at end
                        insertPosition = fileContent.length();
                        log.debug("Inserting lecture content at end of file");
                    }
                } else {
                    // Lecture not found - check if we should create a new one
                    log.debug("Lecture title '{}' not found in content - inserting at end", node.getTitle());
                }
                break;

            case SECTION:
                // For sections, try to find the appropriate lecture first
                if (node.getParent() != null && node.getParent().getTitle() != null) {
                    String lectureTitle = node.getParent().getTitle();
                    Pattern parentLecturePattern = Pattern.compile("^## " + Pattern.quote(lectureTitle), Pattern.MULTILINE);
                    Matcher parentLectureMatcher = parentLecturePattern.matcher(fileContent);

                    if (parentLectureMatcher.find()) {
                        // Found the parent lecture
                        int lectureStart = parentLectureMatcher.start();
                        int lectureEnd = fileContent.length();

                        // Find end of this lecture (next lecture or EOF)
                        int nextLecturePos = fileContent.indexOf("\n## ", lectureStart + 1);
                        if (nextLecturePos > -1) {
                            lectureEnd = nextLecturePos;
                        }

                        // Find insertion point within this lecture - after the last section
                        String lectureContent = fileContent.substring(lectureStart, lectureEnd);
                        Pattern sectionPattern = Pattern.compile("^### ([^\\n]+)", Pattern.MULTILINE);
                        Matcher sectionMatcher = sectionPattern.matcher(lectureContent);

                        int lastSectionPos = -1;
                        while (sectionMatcher.find()) {
                            lastSectionPos = lectureStart + sectionMatcher.start();

                            // If we find the section with our title, use its position
                            if (sectionMatcher.group(1).trim().equals(node.getTitle())) {
                                // Find the end of this section
                                int sectionStart = lastSectionPos;
                                int sectionEnd = lectureEnd;

                                // Look for the next section after this one
                                int nextSectionPos = lectureContent.indexOf("\n### ", sectionMatcher.end());
                                if (nextSectionPos > -1) {
                                    sectionEnd = lectureStart + nextSectionPos;
                                }

                                insertPosition = sectionEnd;
                                log.debug("Found existing section '{}' - inserting at end of section at position {}",
                                        node.getTitle(), insertPosition);
                                break;
                            }
                        }

                        // If we didn't find our section but found others, insert after the last one
                        if (lastSectionPos > -1 && !sectionMatcher.group(1).trim().equals(node.getTitle())) {
                            // Find the next section or lecture
                            int nextSectionPos = fileContent.indexOf("\n### ", lastSectionPos + 1);
                            if (nextSectionPos > -1 && nextSectionPos < lectureEnd) {
                                insertPosition = nextSectionPos;
                            } else {
                                insertPosition = lectureEnd;
                            }
                            log.debug("Inserting section after last section at position {}", insertPosition);
                        } else if (lastSectionPos == -1) {
                            // No sections found - insert after the lecture header
                            insertPosition = lectureStart + lectureContent.indexOf("\n") + 1;
                            log.debug("No sections found - inserting after lecture header at position {}", insertPosition);
                        }
                    } else {
                        log.debug("Parent lecture '{}' not found - inserting section at end", lectureTitle);
                    }
                }
                break;

            case TOPIC:
                // Similar to section but search for the parent section
                if (node.getParent() != null && node.getParent().getTitle() != null) {
                    String sectionTitle = node.getParent().getTitle();
                    Pattern parentSectionPattern = Pattern.compile("^### " + Pattern.quote(sectionTitle), Pattern.MULTILINE);
                    Matcher parentSectionMatcher = parentSectionPattern.matcher(fileContent);

                    if (parentSectionMatcher.find()) {
                        // Found the parent section
                        int sectionStart = parentSectionMatcher.start();
                        int sectionEnd = fileContent.length();

                        // Find end of this section (next section or EOF)
                        int nextSectionPos = fileContent.indexOf("\n### ", sectionStart + 1);
                        if (nextSectionPos > -1) {
                            sectionEnd = nextSectionPos;
                        }

                        // Look for existing topics in this section
                        String sectionContent = fileContent.substring(sectionStart, sectionEnd);
                        Pattern topicPattern = Pattern.compile("^#### ([^\\n]+)", Pattern.MULTILINE);
                        Matcher topicMatcher = topicPattern.matcher(sectionContent);

                        int lastTopicPos = -1;
                        while (topicMatcher.find()) {
                            lastTopicPos = sectionStart + topicMatcher.start();

                            // If we find our topic, use its position
                            if (topicMatcher.group(1).trim().equals(node.getTitle())) {
                                // Find the end of this topic
                                int topicStart = lastTopicPos;
                                int topicEnd = sectionEnd;

                                // Look for next topic
                                int nextTopicPos = sectionContent.indexOf("\n#### ", topicMatcher.end());
                                if (nextTopicPos > -1) {
                                    topicEnd = sectionStart + nextTopicPos;
                                }

                                insertPosition = topicEnd;
                                log.debug("Found existing topic '{}' - inserting at end of topic at position {}",
                                        node.getTitle(), insertPosition);
                                break;
                            }
                        }

                        // If we didn't find our topic but found others, insert after the last one
                        if (lastTopicPos > -1 && !topicMatcher.group(1).trim().equals(node.getTitle())) {
                            // Find the next topic or section
                            int nextTopicPos = fileContent.indexOf("\n#### ", lastTopicPos + 1);
                            if (nextTopicPos > -1 && nextTopicPos < sectionEnd) {
                                insertPosition = nextTopicPos;
                            } else {
                                insertPosition = sectionEnd;
                            }
                            log.debug("Inserting topic after last topic at position {}", insertPosition);
                        } else if (lastTopicPos == -1) {
                            // No topics found - insert after the section header
                            insertPosition = sectionStart + sectionContent.indexOf("\n") + 1;
                            log.debug("No topics found - inserting after section header at position {}", insertPosition);
                        }
                    } else {
                        log.debug("Parent section '{}' not found - inserting topic at end", sectionTitle);
                    }
                }
                break;

            case SLIDE:
                // Handle slides - similar pattern but finding the right topic context is crucial
                if (node.getParent() != null && node.getParent().getTitle() != null) {
                    // First, try to find the parent topic
                    String topicTitle = node.getParent().getTitle();
                    Pattern parentTopicPattern = Pattern.compile("^#### " + Pattern.quote(topicTitle), Pattern.MULTILINE);
                    Matcher parentTopicMatcher = parentTopicPattern.matcher(fileContent);

                    if (parentTopicMatcher.find()) {
                        // Found the parent topic
                        int topicStart = parentTopicMatcher.start();
                        int topicEnd = fileContent.length();

                        // Find end of this topic (next topic, section, or EOF)
                        int nextTopicPos = fileContent.indexOf("\n#### ", topicStart + 1);
                        int nextSectionPos = fileContent.indexOf("\n### ", topicStart + 1);

                        if (nextTopicPos > -1 && (nextSectionPos == -1 || nextTopicPos < nextSectionPos)) {
                            topicEnd = nextTopicPos;
                        } else if (nextSectionPos > -1) {
                            topicEnd = nextSectionPos;
                        }

                        // Look for slides in this topic
                        String topicContent = fileContent.substring(topicStart, topicEnd);

                        // For slides, we use the seq tag pattern
                        Pattern slidePattern = Pattern.compile("^##### \\[seq:(\\d+)\\] ([^\\n]+)", Pattern.MULTILINE);
                        Matcher slideMatcher = slidePattern.matcher(topicContent);

                        // Try to insert based on sequence number
                        int slideSeq = node.getDisplayOrder() != null ? node.getDisplayOrder() : 999;
                        int prevSlidePos = -1;
                        int prevSlideSeq = -1;

                        // Scan all slides to find the right insertion point based on sequence
                        boolean foundExactSlide = false;
                        while (slideMatcher.find()) {
                            String seqStr = slideMatcher.group(1).trim();
                            String slideTitle = slideMatcher.group(2).trim();
                            int currentSeq = Integer.parseInt(seqStr);

                            if (slideTitle.equals(node.getTitle())) {
                                // Found the exact slide we're updating
                                int slideStart = topicStart + slideMatcher.start();
                                int slideEnd = topicEnd;

                                // Find end of this slide
                                int nextSlidePos = topicContent.indexOf("\n##### [seq:", slideMatcher.end());
                                if (nextSlidePos > -1) {
                                    slideEnd = topicStart + nextSlidePos;
                                }

                                insertPosition = slideEnd;
                                foundExactSlide = true;
                                log.debug("Found existing slide '{}' with seq {} - updating at position {}",
                                        node.getTitle(), slideSeq, insertPosition);
                                break;
                            }

                            // Track previous slide for sequence-based insertion
                            if (currentSeq < slideSeq) {
                                prevSlidePos = topicStart + slideMatcher.start();
                                prevSlideSeq = currentSeq;
                            } else if (currentSeq > slideSeq && !foundExactSlide) {
                                // Found a slide with a higher sequence - insert before it
                                insertPosition = topicStart + slideMatcher.start();
                                log.debug("Inserting slide with seq {} before slide with seq {}",
                                        slideSeq, currentSeq);
                                break;
                            }
                        }

                        // If we didn't find the right spot by sequence, insert after the last slide with lower seq
                        if (prevSlidePos > -1 && !foundExactSlide && insertPosition == fileContent.length()) {
                            // Find where this slide ends
                            int nextSlidePos = fileContent.indexOf("\n##### [seq:", prevSlidePos + 1);
                            if (nextSlidePos > -1 && nextSlidePos < topicEnd) {
                                insertPosition = nextSlidePos;
                            } else {
                                insertPosition = topicEnd;
                            }
                            log.debug("Inserting slide with seq {} after slide with seq {}",
                                    slideSeq, prevSlideSeq);
                        } else if (prevSlidePos == -1 && !foundExactSlide && insertPosition == fileContent.length()) {
                            // No slides found with lower seq - insert at beginning of topic
                            insertPosition = topicStart + topicContent.indexOf("\n") + 1;
                            log.debug("No slides with lower seq found - inserting at topic start");
                        }
                    } else {
                        log.debug("Parent topic '{}' not found - inserting slide at end", topicTitle);
                    }
                }
                break;
        }

        // Insert the content at the determined position with proper spacing
        log.info("Final insertion position for node '{}': {}", node.getTitle(), insertPosition);

        // Insert in the file with proper spacing
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