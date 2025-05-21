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
        ContentNode relevantNodeForContext = null;
        if ("UPDATE".equalsIgnoreCase(originalProposal.action()) && originalProposal.targetNodeId() != null) {
            relevantNodeForContext = nodeRepository.findById(originalProposal.targetNodeId()).orElse(null);
        } else if ("ADD".equalsIgnoreCase(originalProposal.action()) && originalProposal.parentNodeId() != null) {
            relevantNodeForContext = nodeRepository.findById(originalProposal.parentNodeId()).orElse(null);
        }

        if (relevantNodeForContext == null) {
            // If still null, try the node passed directly (e.g., from a createNode flow not via proposal)
            relevantNodeForContext = nodeForPathContext;
            if (relevantNodeForContext == null) {
                log.error("Cannot determine context for target file from proposal or direct node. Falling back to first lecture file.");
                return Paths.get(repoRoot, LECTURE_FILES[0]);
            }
        }

        ContentNode lectureNode = findLectureParent(relevantNodeForContext);

        if (lectureNode != null && lectureNode.getTitle() != null) {
            String dbLectureTitle = lectureNode.getTitle().trim(); // Title from DB, e.g., "Lecture 1. Introduction..."
            log.debug("Found parent lecture: '{}' (ID: {}) for determining target file.", dbLectureTitle, lectureNode.getId());

            for (String lectureFileName : LECTURE_FILES) {
                // More robust matching:
                // 1. Extract number from DB lecture title: "Lecture 1..." -> "1"
                // 2. Extract number from filename: "Lecture 1- Intro..." -> "1"
                // 3. Compare numbers. Also check if a significant part of the title matches.
                String dbLectureNumStr = extractLectureNumberFromTitle(dbLectureTitle);
                String fileLectureNumStr = extractLectureNumberFromFilename(lectureFileName);

                if (dbLectureNumStr != null && dbLectureNumStr.equals(fileLectureNumStr)) {
                    log.info("Determined target file: {} for node based on lecture title match: '{}'", lectureFileName, dbLectureTitle);
                    return Paths.get(repoRoot, lectureFileName);
                }
            }
            log.warn("Could not match DB lecture title '{}' to any known LECTURE_FILES by number. Falling back to heuristic for node: {}",
                    dbLectureTitle, nodeForPathContext.getTitle());
        } else {
            log.warn("Could not find parent lecture for node ID {} (Title: {}) to determine target file. Falling back to heuristic.",
                    relevantNodeForContext.getId(), relevantNodeForContext.getTitle());
        }
        return determineTargetFileHeuristic(nodeForPathContext); // Your old heuristic method
    }

    private String extractLectureNumberFromTitle(String title) {
        Pattern pattern = Pattern.compile("^Lecture\\s+(\\d+)[\\.\\s-:]?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractLectureNumberFromFilename(String filename) {
        // Example Filename: "Lecture 1- Introduction to AI and Current Developments.md"
        Pattern pattern = Pattern.compile("^Lecture\\s+(\\d+)[\\-\\s\\.:].*\\.md$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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

    private String handleAddAction(String existingFileContent,
                                   Long parentNodeIdInDb,
                                   ContentNode proposedChildNodeDetails, // Transient node for the NEW child
                                   String newChildMarkdownBlock,      // Full Markdown for the NEW child (e.g. "##### [seq:015] New Slide\n\nBody...")
                                   AiProposalDto originalProposal) {   // Contains parentNodeId, and child's proposed displayOrder, nodeType, title

        if (parentNodeIdInDb == null) {
            log.error("ADD_ACTION_ERROR: ParentNodeId is null. Cannot add child '{}'. Fallback append.", proposedChildNodeDetails.getTitle());
            return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_NULL_PARENT_ID_FOR_" + proposedChildNodeDetails.getTitle());
        }

        ContentNode parentNodeInDb = nodeRepository.findById(parentNodeIdInDb).orElse(null);
        if (parentNodeInDb == null) {
            log.error("ADD_ACTION_ERROR: Cannot find parent node in DB with ID: {}. Cannot add child '{}'. Fallback append.",
                    parentNodeIdInDb, proposedChildNodeDetails.getTitle());
            return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_PARENT_DB_NODE_NOT_FOUND_FOR_" + proposedChildNodeDetails.getTitle());
        }

        log.info("--------------------------------------------------------------------");
        log.info("HANDLE_ADD_ACTION: Adding new {} '{}' under Parent ID: {}, Parent Title: '{}', Parent Type: {}",
                proposedChildNodeDetails.getNodeType(), proposedChildNodeDetails.getTitle(),
                parentNodeInDb.getId(), parentNodeInDb.getTitle(), parentNodeInDb.getNodeType());
        log.debug("Proposed child displayOrder: {}", proposedChildNodeDetails.getDisplayOrder());
        log.info("--------------------------------------------------------------------");

        // 1. Construct Regex to find the parent node's entire block
        String parentHeaderRegexString;
        String parentBlockEndLookaheadRegex = "(?=\\n(?:##|###|####|#####)\\s|$)"; // End before any subsequent header or EOF
        String childHeaderStartPattern; // To find children within the parent block

        // Titles from DB should be trimmed before quoting for regex
        String parentDbTitleForRegex = parentNodeInDb.getTitle() != null ? parentNodeInDb.getTitle().trim() : "";

        switch (parentNodeInDb.getNodeType()) {
            case TOPIC: // Assuming we are adding a SLIDE under a TOPIC
                if (parentDbTitleForRegex.isEmpty()) { /* error log & fallback */ return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_PARENT_TOPIC_DB_TITLE_MISSING");}
                parentHeaderRegexString = String.format("####\\s+(?:[\\d\\.]+\\s+)?%s", Pattern.quote(parentDbTitleForRegex));
                childHeaderStartPattern = "^#####\\s+\\[seq:(\\d+)\\]"; // To find existing slides
                break;
            case SECTION: // Assuming we are adding a TOPIC under a SECTION
                if (parentDbTitleForRegex.isEmpty()) { /* error log & fallback */ return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_PARENT_SECTION_DB_TITLE_MISSING");}
                parentHeaderRegexString = String.format("###\\s+(?:[\\d\\.]+\\s+)?%s", Pattern.quote(parentDbTitleForRegex));
                childHeaderStartPattern = "^####\\s+"; // To find existing topics
                break;
            // Add cases for adding SECTION under LECTURE, LECTURE under COURSE if needed
            default:
                log.warn("ADD_ACTION_WARN: Adding children to parent type {} not fully supported yet. Parent: '{}'. Fallback append.",
                        parentNodeInDb.getNodeType(), parentNodeInDb.getTitle());
                return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_UNSUPPORTED_PARENT_TYPE_" + parentNodeInDb.getTitle());
        }

        log.debug("Constructed parentHeaderRegexString: '{}'", parentHeaderRegexString);
        Pattern parentBlockPattern = Pattern.compile(
                "(^" + parentHeaderRegexString + "\\s*$\\n?(.*?))" + parentBlockEndLookaheadRegex,
                Pattern.MULTILINE | Pattern.DOTALL
        );
        Matcher parentMatcher = parentBlockPattern.matcher(existingFileContent);

        if (parentMatcher.find()) {
            String fullParentBlockWithHeader = parentMatcher.group(1); // Header + Body of parent
            String parentContentBody = parentMatcher.group(2);      // Just the body of the parent
            int parentBlockStartIndex = parentMatcher.start(1);
            int parentBlockEndIndex = parentMatcher.end(1);

            log.info("Found parent block for '{}'. Parent content body length: {}", parentNodeInDb.getTitle(), parentContentBody.length());

            // 2. Find insertion point within the parentContentBody for the new child
            int insertionPointInParentBody = -1;
            Integer newChildDisplayOrder = proposedChildNodeDetails.getDisplayOrder();

            if (newChildDisplayOrder == null) {
                log.warn("ADD_ACTION_WARN: New child '{}' has no displayOrder. Appending within parent.", proposedChildNodeDetails.getTitle());
                // Append at the end of parent's body content, before any potential non-child content that the lookahead might have included
                insertionPointInParentBody = parentContentBody.length(); // Default to end of body
            } else {
                Pattern childSiblingPattern = Pattern.compile(childHeaderStartPattern + ".*$", Pattern.MULTILINE);
                Matcher siblingMatcher = childSiblingPattern.matcher(parentContentBody);
                int lastFoundSiblingEnd = -1; // End position of the last sibling found *before* insertion point
                boolean inserted = false;

                while (siblingMatcher.find()) {
                    lastFoundSiblingEnd = siblingMatcher.end();
                    if (proposedChildNodeDetails.getNodeType() == ContentNode.NodeType.SLIDE) {
                        try {
                            int siblingSeq = Integer.parseInt(siblingMatcher.group(1)); // Group 1 is the sequence number for slides
                            if (newChildDisplayOrder <= siblingSeq) {
                                insertionPointInParentBody = siblingMatcher.start(); // Insert before this sibling
                                inserted = true;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Could not parse sequence number from sibling: {}", siblingMatcher.group(0));
                        }
                    } else { // For TOPIC, SECTION etc., ordering might be simpler (or based on parsing full node numbers if titles have them)
                        // This simplified logic for non-slides just finds the first child and inserts before/after.
                        // Needs more robust logic if multiple non-slide children need specific ordering.
                        // For now, if it's the first child or displayOrder is low, insert at its start.
                        // This part is very basic for non-slides.
                        if (newChildDisplayOrder < 50) { // Arbitrary: assume low order means "at the start"
                            insertionPointInParentBody = siblingMatcher.start();
                            inserted = true;
                            break;
                        }
                    }
                }

                if (!inserted) { // If not inserted before any sibling, append after the last found sibling or at start of body
                    if (lastFoundSiblingEnd != -1) {
                        insertionPointInParentBody = lastFoundSiblingEnd; // Insert after the full block of the last sibling
                    } else {
                        insertionPointInParentBody = 0; // No siblings found, insert at the beginning of parent's body
                    }
                }
            }

            // Construct the new parent body
            StringBuilder newParentBodyBuilder = new StringBuilder(parentContentBody);
            String contentToInsert = "\n" + newChildMarkdownBlock.trim() + "\n"; // Ensure newlines around the new block

            if (insertionPointInParentBody >= 0 && insertionPointInParentBody <= newParentBodyBuilder.length()) {
                newParentBodyBuilder.insert(insertionPointInParentBody, contentToInsert);
            } else { // Should not happen if logic above is correct
                log.warn("Calculated invalid insertionPointInParentBody ({}). Appending child to parent body.", insertionPointInParentBody);
                newParentBodyBuilder.append(contentToInsert);
            }

            String newFullParentBlock = fullParentBlockWithHeader.substring(0, fullParentBlockWithHeader.length() - parentContentBody.length())
                    + newParentBodyBuilder.toString();

            // Replace the old parent block with the new parent block (which now includes the child)
            // This uses a more robust way to reconstruct the file rather than direct replaceFirst on parentMatcher.group(0)
            // because parentMatcher.group(0) might not be the full parent block if parentContentBody was extensive.
            // Instead, we identified parentBlockStartIndex and parentBlockEndIndex

            StringBuilder finalFileContent = new StringBuilder(existingFileContent);
            finalFileContent.replace(parentBlockStartIndex, parentBlockEndIndex, newFullParentBlock);

            log.info("SUCCESS_ADD: Prepared to add new {} '{}' into parent '{}'.",
                    proposedChildNodeDetails.getNodeType(), proposedChildNodeDetails.getTitle(), parentNodeInDb.getTitle());

            return finalFileContent.toString();

        } else {
            log.warn("FAILED_ADD_NO_PARENT_MATCH: Could not find parent block in Markdown for Parent DB ID: {}, Title: '{}'. Regex used for parent header: '{}'. Fallback append.",
                    parentNodeIdInDb, parentNodeInDb.getTitle(), parentHeaderRegexString);
            return fallbackAppend(existingFileContent, newChildMarkdownBlock, "ADD_FAILED_FIND_PARENT_BLOCK_FOR_" + proposedChildNodeDetails.getTitle());
        }
    }
}