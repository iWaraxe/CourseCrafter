package com.coherentsolutions.coursecrafter.infrastructure.git;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
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
    private final ContentNodeRepository nodeRepository;

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
    public boolean syncNodeToFile(ContentNode node, String branchName, AiProposalDto originalProposal) {
        try {
            // Determine which lecture file this content belongs to
            Path targetFile = determineTargetFile(node, originalProposal);

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
            String updatedContent = insertContentAtAppropriateLocation(fileContent, node, nodeContent, originalProposal);

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
    public boolean syncNodeToFileOnly(ContentNode transientNode, String branchName, AiProposalDto originalProposal) { // Added AiProposalDto
        try {
            Path targetFile = determineTargetFile(transientNode, originalProposal); // Uses transientNode
            // ... (null checks, file existence checks) ...
            String fileContent = Files.readString(targetFile);
            String nodeContent = generateNodeContent(transientNode); // Uses transientNode
            // ... (null checks for nodeContent) ...

            // Pass originalProposal here
            String updatedContent = insertContentAtAppropriateLocation(fileContent, transientNode, nodeContent, originalProposal);

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
    // In GitContentSyncService.java
    private Path determineTargetFile(ContentNode nodeForPathContext, AiProposalDto originalProposal) {
        ContentNode relevantNode = null;
        if ("UPDATE".equalsIgnoreCase(originalProposal.action()) && originalProposal.targetNodeId() != null) {
            relevantNode = nodeRepository.findById(originalProposal.targetNodeId()).orElse(null);
        } else if ("ADD".equalsIgnoreCase(originalProposal.action()) && originalProposal.parentNodeId() != null) {
            relevantNode = nodeRepository.findById(originalProposal.parentNodeId()).orElse(null);
        } else {
            // Fallback to the node itself if action/IDs are unclear, though this is less reliable
            relevantNode = nodeForPathContext;
        }

        if (relevantNode == null) {
            log.error("Cannot determine context for target file. Falling back to first lecture file.");
            return Paths.get(repoRoot, LECTURE_FILES[0]);
        }

        ContentNode lectureNode = findLectureParent(relevantNode);

        if (lectureNode != null && lectureNode.getTitle() != null) {
            String lectureTitle = lectureNode.getTitle();
            // Match lectureTitle (e.g., "Lecture 1. Introduction...") against LECTURE_FILES array
            for (String lectureFile : LECTURE_FILES) {
                // Normalize titles for comparison (e.g., remove "Lecture X. " prefix if needed)
                String normalizedLectureFileTitle = lectureFile.replace(".md", "").replaceAll("^Lecture \\d+[\\.\\s-]+", "").trim();
                String normalizedDbLectureTitle = lectureTitle.replaceAll("^Lecture \\d+[\\.\\s-]+", "").trim();

                if (normalizedLectureFileTitle.equalsIgnoreCase(normalizedDbLectureTitle) || lectureFile.contains(normalizedDbLectureTitle) || normalizedDbLectureTitle.contains(normalizedLectureFileTitle)) {
                    log.debug("Determined target file: {} for node based on lecture: {}", lectureFile, lectureTitle);
                    return Paths.get(repoRoot, lectureFile);
                }
            }
            log.warn("Could not match lecture title '{}' to any known LECTURE_FILES. Falling back.", lectureTitle);
        } else {
            log.warn("Could not find parent lecture for node ID {} (Title: {}). Falling back.", relevantNode.getId(), relevantNode.getTitle());
        }

        // Fallback if no match or no lecture parent
        log.debug("Falling back to default file determination logic for node title: {}", nodeForPathContext.getTitle());
        return determineTargetFileHeuristic(nodeForPathContext); // Your old heuristic method
    }

    private ContentNode findLectureParent(ContentNode node) {
        ContentNode current = node;
        while (current != null) {
            if (current.getNodeType() == ContentNode.NodeType.LECTURE) {
                return current;
            }
            if (current.getParent() == null && current.getNodeType() == ContentNode.NodeType.COURSE) { // If we hit course, its children are lectures
                return null; // Or handle differently if proposals can target course directly
            }
            current = current.getParent();
        }
        return null; // Should not happen if structure is correct and node is not course itself
    }


    private Path determineTargetFileHeuristic(ContentNode node) {
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
        StringBuilder contentBuilder = new StringBuilder();

        switch (node.getNodeType()) {
            case COURSE: // Assuming courses are not typically added/updated this way, but for completeness
                contentBuilder.append("# ").append(node.getTitle()).append("\n\n");
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) {
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                }
                break;

            case LECTURE:
                contentBuilder.append("## ").append(node.getTitle()).append("\n\n");
                // Append the actual content proposed for the lecture itself (e.g., intro text)
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) {
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                }
                break;

            case SECTION:
                contentBuilder.append("### ").append(node.getTitle()).append("\n\n");
                // Append the actual content proposed for the section itself
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) {
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                }
                break;

            case TOPIC:
                contentBuilder.append("#### ").append(node.getTitle()).append("\n\n");
                // Append the actual content proposed for the topic itself
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) {
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                }
                break;

            case SLIDE:
                // Format with display order as sequence number
                contentBuilder.append("##### [seq:").append(node.getDisplayOrder() != null ?
                                String.format("%03d", ((Number)node.getDisplayOrder()).intValue()) : "ERR")
                        .append("] ") // Use proposedNodeDetails.getDisplayOrder()
                        .append(node.getTitle()) // This should be the clean title from proposedNodeDetails
                        .append("\n\n");

                // For SLIDES, node.getMarkdownContent() IS the body (SCRIPT, VISUALS etc.)
                // as proposed by the AI.
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) { // node.getMarkdownContent() comes from transientNode which gets it from proposal.slideContentShouldBe()
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                } else {
                    log.warn("Proposed content for slide '{}' was empty. Adding a placeholder.", node.getTitle()); // THIS LINE
                    contentBuilder.append("<!-- AI Proposed an empty slide body. Please review. -->\n\n");
                }
                break;

            default: // MODULE or other types if you add them
                log.warn("Unsupported node type for content generation in Git sync: {}", node.getNodeType());
                // Generic fallback: Title + Content
                contentBuilder.append("#?#?# ").append(node.getTitle()).append(" (Unknown Type: ").append(node.getNodeType()).append(")\n\n");
                if (node.getMarkdownContent() != null && !node.getMarkdownContent().isBlank()) {
                    contentBuilder.append(node.getMarkdownContent()).append("\n\n");
                }
                return null;
        }

        return contentBuilder.toString();
    }

    /**
     * Insert/Update content at an appropriate location in the file based on node type and content
     */
    private String insertContentAtAppropriateLocation(String existingFileContent, ContentNode proposedNodeDetails, String newContentBlockForNode, AiProposalDto originalProposal) {
        String action = originalProposal.action();
        Long targetNodeIdForUpdate = originalProposal.targetNodeId();
        Long parentNodeIdForAdd = originalProposal.parentNodeId();
        // Now you have the original proposal details!

        log.debug("Attempting to insert/update node: '{}' (Type: {}), Action: {}",
                proposedNodeDetails.getTitle(), proposedNodeDetails.getNodeType(), action);

        if ("UPDATE".equalsIgnoreCase(action) && targetNodeIdForUpdate != null) {
            return handleUpdateAction(existingFileContent, targetNodeIdForUpdate, proposedNodeDetails, newContentBlockForNode, originalProposal);
        } else if ("ADD".equalsIgnoreCase(action) && parentNodeIdForAdd != null) {
            return handleAddAction(existingFileContent, parentNodeIdForAdd, proposedNodeDetails, newContentBlockForNode, originalProposal);
        } else {
            // ... (fallback logic) ...
            log.warn("Unsupported action or missing IDs for intelligent placement. Action: {}, TargetID: {}, ParentID: {}. Appending to end as fallback.",
                    action, targetNodeIdForUpdate, parentNodeIdForAdd);
            return existingFileContent + "\n\n" + "<!-- FALLBACK APPENDED CONTENT FOR TITLE: " + proposedNodeDetails.getTitle() + " -->\n" + newContentBlockForNode + "\n<!-- END FALLBACK -->\n\n";
        }
    }

    // We will implement handleUpdateAction and handleAddAction in subsequent steps.
    // Stubs for now:
    private String handleUpdateAction(String existingFileContent,
                                      Long targetNodeIdInDb,
                                      ContentNode proposedNodeDetails,
                                      String newFullMarkdownBlockForNode,
                                      AiProposalDto originalProposal) {

        if (targetNodeIdInDb == null) {
            log.error("UPDATE_ACTION_ERROR: TargetNodeId is null. Proposal title: {}", originalProposal.title());
            return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_NULL_TARGET_ID_FOR_" + originalProposal.title());
        }

        ContentNode currentNodeInDb = nodeRepository.findById(targetNodeIdInDb).orElse(null);

        if (currentNodeInDb == null) {
            log.error("UPDATE_ACTION_ERROR: Cannot find existing node in DB with ID: {}. Proposal title: {}",
                    targetNodeIdInDb, originalProposal.title());
            return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_DB_NODE_NOT_FOUND_FOR_" + originalProposal.title());
        }

        log.info("--------------------------------------------------------------------");
        log.info("HANDLE_UPDATE_ACTION for DB Node ID: {}, Current DB Title: '{}', Type: {}",
                currentNodeInDb.getId(), currentNodeInDb.getTitle(), currentNodeInDb.getNodeType());
        log.debug("Original AI Proposal had action: {}, title: '{}', targetNodeId: {}",
                originalProposal.action(), originalProposal.title(), originalProposal.targetNodeId());
        log.debug("Proposed transient node details: Title: '{}', DispOrder: {}, NodeType: {}",
                proposedNodeDetails.getTitle(), proposedNodeDetails.getDisplayOrder(), proposedNodeDetails.getNodeType());
        log.debug("New full Markdown block to insert/replace (first 100): '{}'",
                newFullMarkdownBlockForNode.substring(0, Math.min(100, newFullMarkdownBlockForNode.length())).replace("\n", "\\n"));
        log.info("--------------------------------------------------------------------");


        ContentNode.NodeType nodeTypeToUpdate = currentNodeInDb.getNodeType();
        String oldHeaderRegexString;
        String blockEndLookaheadRegex = "(?=\\n(?:##|###|####|#####)\\s|$)";

        // Titles from DB should be trimmed before quoting for regex
        String dbTitleForRegex = currentNodeInDb.getTitle() != null ? currentNodeInDb.getTitle().trim() : "";

        switch (nodeTypeToUpdate) {
            case SLIDE:
                if (currentNodeInDb.getDisplayOrder() == null || dbTitleForRegex.isEmpty()) {
                    log.error("UPDATE_ACTION_ERROR: Existing slide (ID: {}) in DB is missing displayOrder or title. DB Title: '{}', DB DispOrder: {}. Cannot reliably find in Markdown.",
                            targetNodeIdInDb, dbTitleForRegex, currentNodeInDb.getDisplayOrder());
                    return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_SLIDE_DB_DATA_MISSING_" + originalProposal.title());
                }
                oldHeaderRegexString = String.format(
                        "#####\\s+\\[seq:%03d\\]\\s+%s", // Ensure displayOrder is an Integer
                        ((Number)currentNodeInDb.getDisplayOrder()).intValue(), // Cast to Number then intValue
                        Pattern.quote(dbTitleForRegex)
                );
                break;
            case TOPIC:
                if (dbTitleForRegex.isEmpty()) {
                    log.error("UPDATE_ACTION_ERROR: Existing topic (ID: {}) in DB is missing title. Cannot find.", targetNodeIdInDb);
                    return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_TOPIC_DB_TITLE_MISSING_" + originalProposal.title());
                }
                // Regex: #### (optional "1.2.3. ")Actual Title From DB
                oldHeaderRegexString = String.format(
                        "####\\s+(?:[\\d\\.]+\\s+)?%s",
                        Pattern.quote(dbTitleForRegex)
                );
                break;
            // ... (cases for SECTION, LECTURE - ensure dbTitleForRegex is used)
            case SECTION:
                if (dbTitleForRegex.isEmpty()) { /* ... error log ... */ return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_SECTION_DB_TITLE_MISSING_" + originalProposal.title());}
                oldHeaderRegexString = String.format("###\\s+(?:[\\d\\.]+\\s+)?%s", Pattern.quote(dbTitleForRegex));
                break;
            case LECTURE:
                if (dbTitleForRegex.isEmpty()) { /* ... error log ... */ return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_LECTURE_DB_TITLE_MISSING_" + originalProposal.title());}
                oldHeaderRegexString = String.format("##\\s+(?:[\\d\\.]+\\s+)?%s", Pattern.quote(dbTitleForRegex));
                break;
            default:
                log.warn("UPDATE_ACTION_WARN: NodeType {} not specifically handled for regex header construction. Proposal: {}. Fallback append.",
                        nodeTypeToUpdate, originalProposal.title());
                return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_TYPE_NOT_HANDLED_FOR_" + originalProposal.title());
        }

        log.debug("Constructed oldHeaderRegexString for finding existing block: '{}'", oldHeaderRegexString);

        Pattern oldBlockPattern = Pattern.compile(
                "(^" + oldHeaderRegexString + "\\s*$\\n?(.*?))" + blockEndLookaheadRegex,
                Pattern.MULTILINE | Pattern.DOTALL
        );

        Matcher matcher = oldBlockPattern.matcher(existingFileContent);

        // Log a snippet of the file content where we expect to find the match, for easier comparison
        int searchStartIndex = 0;
        if (existingFileContent.length() > 1000) { // If file is large, try to narrow down search for logging
            // This is a rough heuristic, might need better way to find approximate region
            int approxTitlePos = existingFileContent.indexOf(dbTitleForRegex);
            if (approxTitlePos > 500) {
                searchStartIndex = approxTitlePos - 500;
            }
        }
        log.debug("Searching for old block in file content snippet (approx 1000 chars around expected title, or start): \n--SNIPPET START--\n{}\n--SNIPPET END--",
                existingFileContent.substring(searchStartIndex, Math.min(searchStartIndex + 1000, existingFileContent.length())).replace("\n", "\\n"));


        if (matcher.find()) {
            // ... (your existing successful match logic) ...
            String oldBlockFound = matcher.group(1);
            log.info("SUCCESS_UPDATE: Found existing block for {} '{}'. Replacing.",
                    nodeTypeToUpdate, currentNodeInDb.getTitle());
            log.debug("Old block content (first 200): '{}'", oldBlockFound.substring(0, Math.min(200, oldBlockFound.length())).replace("\n", "\\n"));
            log.debug("New block content (first 200): '{}'", newFullMarkdownBlockForNode.substring(0, Math.min(200, newFullMarkdownBlockForNode.length())).replace("\n", "\\n"));

            String replacementString = newFullMarkdownBlockForNode.trim();
            String updatedFileContent = matcher.replaceFirst(Matcher.quoteReplacement(replacementString));

            if (existingFileContent.equals(updatedFileContent)) {
                log.warn("WARN_UPDATE_NO_CHANGE: Update for {} '{}' resulted in no change to file content. Regex or content identical?", nodeTypeToUpdate, currentNodeInDb.getTitle());
            } else {
                log.info("SUCCESS_UPDATE_CONTENT_MODIFIED: Content for {} '{}' was modified.", nodeTypeToUpdate, currentNodeInDb.getTitle());
            }
            return updatedFileContent;
        } else {
            log.warn("FAILED_UPDATE_NO_MATCH: Could not find existing block in Markdown for {} '{}' (ID: {}). Regex used for header: '{}'",
                    nodeTypeToUpdate, currentNodeInDb.getTitle(), targetNodeIdInDb, oldHeaderRegexString);
            // Log more details about the file and target
            if (nodeTypeToUpdate == ContentNode.NodeType.SLIDE) {
                log.warn("Failed Slide Details - DB Seq: {}, DB Title: '{}'", currentNodeInDb.getDisplayOrder(), currentNodeInDb.getTitle());
            }
            return fallbackAppend(existingFileContent, newFullMarkdownBlockForNode, "UPDATE_FAILED_FIND_OLD_BLOCK_FOR_" + originalProposal.title());
        }
    }

    // Helper for fallback
    private String fallbackAppend(String existingFileContent, String newContent, String reason) {
        return existingFileContent + "\n\n" + "<!-- FALLBACK APPEND (" + reason + ") -->\n" + newContent + "\n<!-- END FALLBACK -->\n\n";
    }

    private String handleAddAction(String existingFileContent, Long parentNodeId, ContentNode proposedNodeDetails, String newContentBlockForNode, AiProposalDto originalProposal) {
        log.warn("handleAddAction not fully implemented for Parent ID: {}. New Node Title: {}. Appending for now.", parentNodeId, proposedNodeDetails.getTitle());
        return existingFileContent + "\n\n" + "<!-- ADD PLACEHOLDER UNDER PARENT ID " + parentNodeId + " FOR TITLE: " + proposedNodeDetails.getTitle() + " -->\n" + newContentBlockForNode + "\n<!-- END ADD PLACEHOLDER -->\n\n";
    }
}