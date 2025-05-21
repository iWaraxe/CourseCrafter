package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalListDto;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedAnalyzerService {

    private final ContentHierarchyService hierarchyService;
    private final ContentNodeRepository contentNodeRepository; // Assuming this is used, if not, can be removed
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analyzes new content and generates proposals for integrating it
     * into the existing content structure of a specific course.
     * This is the primary method to use.
     */
    public List<AiProposalDto> analyzeContentForCourse(String courseName, String cleanedContent) {
        log.info("analyzeContentForCourse called for course: '{}', cleanedContent length: {} characters",
                courseName, cleanedContent.length());

        // 1. Get detailed course structure including DB IDs for the AI context
        String courseContextWithIds = hierarchyService.generateDetailedOutlineContext(courseName); // Ensure this method includes "(ID: X)"
        log.debug("Course context with IDs length: {} characters. Preview (first 300): {}",
                courseContextWithIds.length(),
                courseContextWithIds.substring(0, Math.min(300, courseContextWithIds.length())).replace("\n", "\\n"));

        // 2. Construct the System Prompt for the AI
        // Fetch the course root ID for fallback parentNodeId suggestions by AI
        Long courseRootId = contentNodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                .stream()
                .filter(node -> node.getTitle().equalsIgnoreCase(courseName)) // Case-insensitive match for course name
                .map(ContentNode::getId)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Could not find specific course '{}' by title for root ID fallback, attempting to find any course.", courseName);
                    return contentNodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                            .stream().findFirst().map(ContentNode::getId).orElse(1L); // Absolute fallback
                });

        String systemPrompt = String.format("""
            You are CourseCrafter AI, an expert system for educational content analysis and integration.

            You will be given:
            1. The current detailed structure of the "%s" course. IMPORTANT: This structure includes database IDs for existing nodes, like "(ID: 123)".
            2. New content to be integrated into this course.

            Your primary goal is to **UPDATE EXISTING CONTENT** whenever possible. Only suggest adding new nodes (Lectures, Sections, Topics, Slides) if the new information is substantial and clearly does not fit within any existing structure.

            **Decision Process for Integration:**
            1.  **Identify Target Location:**
                *   Pinpoint the most relevant existing **Lecture (ID: L)**, then **Section (ID: S)**, then **Topic (ID: T)**.
                *   Within that Topic, identify the most relevant existing **Slide (ID: SL, Current Title: '[seq:XXX] Current Slide Title')**.
            2.  **Update Existing Slide (Preferred):**
                *   If a relevant Slide (ID: SL) is found: Propose an **UPDATE** action.
                *   The `targetNodeId` in your JSON MUST be **SL (the ID of the existing Slide)**.
                *   The `slideContentShouldBe` field in JSON should be the *complete new proposed body for that specific slide*, including all its `###### SCRIPT/VISUAL/NOTES/DEMONSTRATION` components, reflecting your update.
                *   If the title of the slide is also changing, the `title` field in JSON should be the new title. The `displayOrder` should be the existing sequence number of the slide being updated, unless you are also proposing to change its sequence.
            3.  **Add New Slide (If Necessary):**
                *   If no existing Slide is a suitable fit, but the Topic (ID: T) is correct, propose an **ADD** action for a new **SLIDE**.
                *   The `parentNodeId` MUST be **T (the ID of the parent Topic)**.
                *   **Crucially, suggest a `displayOrder` (e.g., 15, 25) for this new slide.** This number should logically place the new slide between existing slides in that Topic. If it's the last slide, increment from the last known sequence.
                *   The `slideContentShouldBe` field should be the full body of the new slide, correctly formatted with `###### SCRIPT`, etc.
            4.  **Update LECTURE/SECTION/TOPIC Direct Content:**
                *   If the new information is introductory or summary content for an existing Lecture, Section, or Topic (not fitting into a slide):
                *   Propose an **UPDATE** action. `targetNodeId` is the ID of that Lecture/Section/Topic.
                *   The `content` field in JSON should contain this direct Markdown content. `slideContentShouldBe` would be null.
            5.  **Add New Topic/Section/Lecture (Rarely):**
                *   The `parentNodeId` MUST be the ID of the correct parent (e.g., Section ID for a new Topic). If adding a new Lecture, `parentNodeId` should be the course root ID: %d.

            **JSON Output Schema:**
            Return a JSON array of precise proposals:
            [{
              "targetNodeId": Long?,      // REQUIRED for action=UPDATE. The ID of the existing LECTURE/SECTION/TOPIC/SLIDE to modify. Null for action=ADD.
              "parentNodeId": Long?,      // REQUIRED for action=ADD. The ID of the parent node. For new Lectures, use Course root ID %d. Null for action=UPDATE (unless re-parenting, which is not supported now).
              "nodeType": "LECTURE|SECTION|TOPIC|SLIDE",
              "action": "ADD|UPDATE",
              "componentTypeToUpdate": "NONE", // Field kept for potential future use, but for now, always provide full slide body for updates.
              "slideContentShouldBe": String?, // For ADD/UPDATE SLIDE: The *entire new/updated body* of the slide (all SCRIPT/VISUAL/etc. components). Null for non-slide types unless it's their direct content.
              "title": String,            // New or updated title. For new slides, DO NOT include [seq:XXX] here.
              "nodeNumber": String?,      // Full desired node number (e.g., "1.2.3"). For ADDED SLIDES, include the AI-determined sequence part like ".015".
              "displayOrder": Integer?,   // For ADDED SLIDES, the integer part of your suggested sequence (e.g., 15). For UPDATED SLIDES, the existing or new sequence if changed.
              "content": String?,         // For ADD/UPDATE of LECTURE/SECTION/TOPIC direct content (e.g., introduction). Null for SLIDES (use slideContentShouldBe).
              "rationale": String
            }]

            Ensure all IDs (`targetNodeId`, `parentNodeId`) are Long values from the provided course structure.
            For new slides, the `title` should be clean. The `displayOrder` and `nodeNumber` should reflect the sequence.
            """, courseName, courseRootId, courseRootId); // Pass courseName and courseRootId

        log.debug("System prompt for AI (length {}): \n{}", systemPrompt.length(), systemPrompt);

        String userPrompt = String.format("""
            # "%s" COURSE STRUCTURE (WITH IDs):
            %s

            # NEW CONTENT TO INTEGRATE:
            %s
            """, courseName, courseContextWithIds, cleanedContent);

        log.debug("User prompt for AI (content part length {}). Course context length: {}",
                cleanedContent.length(), courseContextWithIds.length());
        if (log.isTraceEnabled()){
            log.trace("Full User prompt for AI: \n{}", userPrompt);
        } else {
            log.debug("User prompt (first 500 chars of new content): {}",
                    cleanedContent.length() > 500 ? cleanedContent.substring(0, 500) + "..." : cleanedContent);
        }


        long startTime = System.currentTimeMillis();

        log.debug("User prompt for AI (content part length {}). Course context length: {}",
                cleanedContent.length(), courseContextWithIds.length());

        if (log.isTraceEnabled()) { // Or use debug if you want it more often initially
            log.trace("Full Course Context with IDs sent to AI for course '{}':\n{}", courseName, courseContextWithIds);
        }

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call();
        long endTime = System.currentTimeMillis();

        log.info("AI response received in {} ms", endTime - startTime);

        List<AiProposalDto> proposals = null;
        try {
            String jsonResponse = response.content();
            log.debug("Raw AI JSON response (first 500 chars): {}",
                    jsonResponse.length() > 500 ? jsonResponse.substring(0, 500) + "..." : jsonResponse);

            saveResponseToFile(jsonResponse, "ai_proposals_for_" + courseName.replaceAll("[^a-zA-Z0-9.-]", "_") +
                    "_" + System.currentTimeMillis() + ".json");

            try {
                log.debug("Attempting to parse AI response as AiProposalListDto (e.g., {\"proposals\": [...]})...");
                AiProposalListDto proposalList = objectMapper.readValue(jsonResponse, AiProposalListDto.class);
                proposals = proposalList.proposals();
                log.info("Successfully parsed AI response as AiProposalListDto, found {} proposals.", proposals.size());
            } catch (Exception e) {
                log.warn("Failed to parse AI response as AiProposalListDto: {}. Attempting to parse as direct List<AiProposalDto> (e.g., [...])...", e.getMessage());
                proposals = objectMapper.readValue(jsonResponse, new TypeReference<List<AiProposalDto>>() {});
                log.info("Successfully parsed AI response as direct List<AiProposalDto>, found {} proposals.", proposals.size());
            }

            if (proposals != null) {
                logProposalsDetails(proposals);
            } else {
                log.warn("AI response parsed, but 'proposals' list is null. Defaulting to empty list.");
                proposals = List.of();
            }

        } catch (Exception e) {
            log.error("FATAL: Failed to parse AI suggestions JSON: {}. Raw response was: {}", e.getMessage(), response.content().substring(0, Math.min(1000,response.content().length() )));
            proposals = List.of(); // Return empty list on fatal parsing error
            // Depending on desired behavior, you might rethrow a custom exception
            // throw new AiProposalParsingException("Failed to parse AI suggestions", e);
        }
        return proposals;
    }

    /**
     * Legacy/simplified version. Deprecated in favor of analyzeContentForCourse.
     * Kept for backward compatibility if called from older code paths.
     */
    @Deprecated
    public List<AiProposalDto> analyzeContent(String newContent) {
        log.warn("Deprecated analyzeContent(String newContent) called. Consider using analyzeContentForCourse for better results.");
        // Fallback to a generic course name or try to infer it if possible.
        // For now, using a placeholder. This will likely produce suboptimal AI proposals.
        String genericCourseName = "GenericCourse"; // Placeholder
        String courseContext = hierarchyService.generateLlmOutlineContext(); // This doesn't include IDs.

        log.debug("analyzeContent (generic) called with content length: {} characters", newContent.length());
        log.debug("Generic course context length: {} characters", courseContext.length());

        String systemPrompt = """
            You are CourseCrafter AI, an expert system for educational content analysis.
            You will be given:
            1. The current structure and content of a generic course.
            2. New content to be integrated into this course.
            Analyze where and how this new content should be integrated.
            Return a JSON array of proposals using the schema:
            [{
              "targetNodeId": Long?, "parentNodeId": Long?, "nodeType": "...", "action": "...",
              "slideContentShouldBe": String?, "title": String, "nodeNumber": String?,
              "displayOrder": Integer?, "content": String?, "rationale": String, "componentTypeToUpdate": "NONE"
            }]
            Provide best guesses for IDs if the context is generic.
            """;
        // Note: Simplified schema in prompt for deprecated method.

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(String.format("""
                    # GENERIC COURSE STRUCTURE
                    %s

                    # NEW CONTENT TO INTEGRATE
                    %s
                    """, courseContext, newContent))
                .call();

        String jsonResponse = response.content();
        log.debug("AI analysis response raw (generic): {}", jsonResponse);
        List<AiProposalDto> proposals = null;
        try {
            try {
                AiProposalListDto proposalList = objectMapper.readValue(jsonResponse, AiProposalListDto.class);
                proposals = proposalList.proposals();
            } catch (Exception e) {
                proposals = objectMapper.readValue(jsonResponse, new TypeReference<List<AiProposalDto>>() {});
            }
            logProposalsDetails(proposals);
            return proposals;
        } catch (Exception e) {
            log.error("Failed to parse AI suggestions (generic): {}", e.getMessage(), e);
            return List.of();
        }
    }


    /**
     * Refines a specific content proposal's "slideContentShouldBe" or "content" field.
     * Does not alter other fields of the proposal.
     */
    public AiProposalDto refineProposal(AiProposalDto proposalToRefine, String existingTargetMarkdownContent) {
        log.debug("Refining proposal for title: '{}', action: {}", proposalToRefine.title(), proposalToRefine.action());

        String contentToRefine = "SLIDE".equals(proposalToRefine.nodeType()) ?
                proposalToRefine.slideContentShouldBe() : proposalToRefine.content();

        if (contentToRefine == null || contentToRefine.isBlank()) {
            log.warn("No content found in proposal to refine for title: '{}'. Returning original.", proposalToRefine.title());
            return proposalToRefine;
        }

        var refinementResponse = chatClient.prompt()
                .system("""
                    You are CourseCrafter AI, an expert in educational content creation and refinement.
                    You will be given Markdown content for a course. Your task is to refine it.
                    Refinement includes:
                    1. Ensuring consistent terminology and style with typical educational material.
                    2. Checking for appropriate length and depth for the likely context (e.g., a slide component).
                    3. Improving flow and readability.
                    4. Adhering to Markdown best practices.
                    Return ONLY the refined Markdown content. Do not add any extra explanations or introductions.
                    If the content is already good, return it as is.
                    """)
                .user(String.format("""
                    # CONTEXT: ORIGINAL PROPOSAL DETAILS
                    Action: %s
                    Title: %s
                    Node Type: %s
                    Rationale for original proposal: %s

                    # EXISTING MARKDOWN CONTENT AT TARGET LOCATION (if applicable, for context)
                    ```markdown
                    %s
                    ```

                    # PROPOSED MARKDOWN CONTENT TO REFINE
                    ```markdown
                    %s
                    ```
                    """,
                        proposalToRefine.action(),
                        proposalToRefine.title(),
                        proposalToRefine.nodeType(),
                        proposalToRefine.rationale(),
                        existingTargetMarkdownContent != null ? existingTargetMarkdownContent : "N/A (This is a new content addition or no existing content provided)",
                        contentToRefine
                ))
                .call();

        String refinedMarkdown = refinementResponse.content();

        // Clean common AI wrappings
        refinedMarkdown = refinedMarkdown.trim();
        if (refinedMarkdown.startsWith("```markdown")) {
            refinedMarkdown = refinedMarkdown.substring("```markdown".length());
        } else if (refinedMarkdown.startsWith("```")) {
            refinedMarkdown = refinedMarkdown.substring("```".length());
        }
        if (refinedMarkdown.endsWith("```")) {
            refinedMarkdown = refinedMarkdown.substring(0, refinedMarkdown.length() - "```".length());
        }
        refinedMarkdown = refinedMarkdown.trim();

        log.debug("Refined content length for '{}': {}", proposalToRefine.title(), refinedMarkdown.length());

        // Create a new DTO with the refined content, keeping other fields the same.
        if ("SLIDE".equals(proposalToRefine.nodeType())) {
            return new AiProposalDto(
                    proposalToRefine.targetNodeId(), proposalToRefine.parentNodeId(), proposalToRefine.nodeType(),
                    proposalToRefine.action(), proposalToRefine.title(), proposalToRefine.nodeNumber(),
                    refinedMarkdown, // refined slideContentShouldBe
                    proposalToRefine.rationale(), proposalToRefine.displayOrder(),
                    proposalToRefine.componentTypeToUpdate(), // Keep original
                    proposalToRefine.content() // Keep original (should be null for SLIDE if using slideContentShouldBe)
            );
        } else { // LECTURE, SECTION, TOPIC
            return new AiProposalDto(
                    proposalToRefine.targetNodeId(), proposalToRefine.parentNodeId(), proposalToRefine.nodeType(),
                    proposalToRefine.action(), proposalToRefine.title(), proposalToRefine.nodeNumber(),
                    proposalToRefine.slideContentShouldBe(), // Keep original (should be null for non-SLIDE)
                    proposalToRefine.rationale(), proposalToRefine.displayOrder(),
                    proposalToRefine.componentTypeToUpdate(), // Keep original
                    refinedMarkdown // refined content
            );
        }
    }

    // Helper method to log details of parsed proposals
    private void logProposalsDetails(List<AiProposalDto> proposals) {
        if (proposals == null || proposals.isEmpty()) {
            log.info("No AI proposals were parsed or generated.");
            return;
        }
        log.info("Logging details for {} AI proposals:", proposals.size());
        for (int i = 0; i < proposals.size(); i++) {
            AiProposalDto prop = proposals.get(i);
            String contentPreview = "N/A";
            if (prop.slideContentShouldBe() != null && !prop.slideContentShouldBe().isEmpty()) {
                contentPreview = prop.slideContentShouldBe().substring(0, Math.min(70, prop.slideContentShouldBe().length())).replace("\n", "\\n") + "...";
            } else if (prop.content() != null && !prop.content().isEmpty()) {
                contentPreview = prop.content().substring(0, Math.min(70, prop.content().length())).replace("\n", "\\n") + "...";
            }

            log.info("  Proposal #{}: Action='{}', Type='{}', Title='{}', TargetID={}, ParentID={}, DispOrder={}, NodeNum='{}', CompToUpdate='{}', ContentPreview='{}'",
                    i,
                    prop.action(),
                    prop.nodeType(),
                    prop.title(),
                    prop.targetNodeId(),
                    prop.parentNodeId(),
                    prop.displayOrder(),
                    prop.nodeNumber(),
                    prop.componentTypeToUpdate(),
                    contentPreview
            );
        }
    }

    // Helper method to save large responses to file for inspection
    private void saveResponseToFile(String content, String filename) {
        try {
            Path debugDir = Paths.get("debug-logs");
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }
            Path filePath = debugDir.resolve(filename);
            Files.writeString(filePath, content);
            log.info("Saved full AI response to: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not save AI response to file '{}': {}", filename, e.getMessage());
        }
    }
}