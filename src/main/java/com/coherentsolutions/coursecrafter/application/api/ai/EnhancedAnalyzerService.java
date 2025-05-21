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
    private final ContentNodeRepository contentNodeRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analyzes new content and generates proposals for integrating it
     * into the existing content structure
     */
    public List<AiProposalDto> analyzeContent(String cleanedContent, String courseName) {
        log.debug("analyzeContent called with course: '{}', content length: {} characters",
                courseName, cleanedContent.length());

        // Get course structure as formatted text for LLM context
        String courseContext = hierarchyService.generateDetailedOutlineContext(courseName);
        log.debug("Course context length: {} characters", courseContext.length());

        // Format the system prompt with courseName for better traceability
        String systemPrompt = String.format("""
            You are CourseCrafter AI, an expert system for educational content analysis and integration.

            You will be given:
            1. The current detailed structure of the "%s" course, including slide sequence numbers and component types (SCRIPT, VISUAL, NOTES, DEMONSTRATION) within each slide.
            2. New content to be integrated into this course.

            Your primary goal is to **UPDATE EXISTING CONTENT** whenever possible. Only suggest adding new nodes (Lectures, Sections, Topics, Slides) if the new information is substantial and clearly does not fit within any existing structure.

            **Decision Process for Integration:**

            1.  **Identify Target Location:**
                *   Pinpoint the most relevant existing **Lecture**, then **Section**, then **Topic** for the new content.
                *   Within that Topic, identify the most relevant existing **Slide** (by its `[seq:XXX] Title`).
            2.  **Update Existing Slide Component (Preferred):**
                *   If a relevant Slide is found, determine which **component (SCRIPT, VISUAL, NOTES, DEMONSTRATION)** is the most appropriate place for the new information.
                *   Propose an **UPDATE** action for that specific slide and component.
                *   Your "content" field in the JSON should be the *complete new content for that specific component*.
                *   The `targetNodeId` should be the ID of the **Slide**.
                *   The `componentType` field in the JSON should specify SCRIPT, VISUAL, NOTES, or DEMONSTRATION.
            3.  **Add New Slide (If Necessary):**
                *   If no existing Slide is a suitable fit, but the Topic is correct, propose an **ADD** action for a new **SLIDE**.
                *   **Crucially, suggest a new sequence number for this slide.** This number should be logically placed between existing slides in that Topic (e.g., if slides 010 and 030 exist, you might suggest 020, or 015 if it's closely related to 010). If it's the last slide, increment from the last known sequence.
                *   The "content" field should be the full body of the new slide, including its own `###### SCRIPT`, `###### VISUAL`, etc., components.
                *   The `parentNodeId` should be the ID of the **Topic**.
            4.  **Add New Topic/Section/Lecture (Rarely):**
                *   Only if the content represents a completely new subject area not covered, propose adding a new Topic, Section, or (very rarely) Lecture. Provide clear rationale.

            **JSON Output Schema (Revised for Clarity):**
            Return a JSON array of precise proposals following this schema:
            [{
                "targetNodeId": Long?,      // ID of existing SLIDE to modify. Null for new nodes OR if updating a LECTURE/SECTION/TOPIC's direct intro content.
                "parentNodeId": Long,       // Parent ID: For new SLIDE (Topic ID), new TOPIC (Section ID), etc.
                "nodeType": "LECTURE|SECTION|TOPIC|SLIDE", // The type of node being ADDED or UPDATED.
                "action": "ADD|UPDATE", // DELETE is a separate flow for now.
                // **** NEW/MODIFIED FIELDS FOR SLIDE COMPONENT UPDATES ****
                "componentTypeToUpdate": "SCRIPT|VISUAL|NOTES|DEMONSTRATION|NONE", // Specify component if action=UPDATE and nodeType=SLIDE. 'NONE' if updating slide title or its direct markdownContent.
                "slideContentShouldBe": String?, // For action=UPDATE nodeType=SLIDE: The *entire new proposed body* of the slide (all components).
                // For action=ADD nodeType=SLIDE: The *entire body* of the new slide.
                // For LECTURE/SECTION/TOPIC: The direct intro content for that node.
                // **** END NEW/MODIFIED FIELDS ****
                "title": String,            // Title for the node. If UPDATE, this is the NEW title.
                "nodeNumber": String?,      // Full node number (e.g., "1.2.3"). For ADDED SLIDES, this MUST include the new [seq:XXX] you determine.
                "displayOrder": Integer?,   // For ADDED SLIDES, this is the integer part of your suggested [seq:XXX].
                "content": String,          // DEPRECATED in favor of slideContentShouldBe or component specific content.
                // OR: if updating a LECTURE/SECTION/TOPIC's *direct* markdown content (not its children slides).
                "rationale": String
            }]

            Be extremely precise about placement. When updating a slide, provide the *complete new content for the specified component* or the *entire new slide body if slideContentShouldBe is used*.
            For new slides, the `title` should NOT include `[seq:XXX]`, but the `nodeNumber` and `displayOrder` should reflect the sequence.
        """, courseName);

        log.debug("System prompt: {}", systemPrompt);

        // Format the user prompt for debugging
        String userPrompt = String.format("""
        # COURSE STRUCTURE
        %s
        
        # NEW CONTENT TO INTEGRATE
        %s
        """, courseContext, cleanedContent);

        // Log only the first 500 chars of user prompt to avoid overwhelming logs
        log.debug("User prompt (first 500 chars): {}",
                userPrompt.length() > 500 ? userPrompt.substring(0, 500) + "..." : userPrompt);

        long startTime = System.currentTimeMillis();
        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call();
        long endTime = System.currentTimeMillis();

        log.debug("AI response received in {} ms", endTime - startTime);

        try {
            String jsonResponse = response.content();
            log.debug("AI response (first 500 chars): {}",
                    jsonResponse.length() > 500 ? jsonResponse.substring(0, 500) + "..." : jsonResponse);

            // Save the full response to a file for inspection if needed
            saveResponseToFile(jsonResponse, "ai_response_" + courseName.replaceAll("[^a-zA-Z0-9]", "_") +
                    "_" + System.currentTimeMillis() + ".json");

            try {
                // First try to parse as AiProposalListDto
                log.debug("Attempting to parse as AiProposalListDto...");
                AiProposalListDto proposalList = objectMapper.readValue(jsonResponse, AiProposalListDto.class);
                List<AiProposalDto> proposals = proposalList.proposals();
                log.debug("Successfully parsed as AiProposalListDto with {} proposals", proposals.size());

                // Log each proposal
                logProposals(proposals);

                return proposals;
            } catch (Exception e) {
                log.debug("Failed to parse as AiProposalListDto: {}", e.getMessage());
                log.debug("Attempting to parse as direct List<AiProposalDto>...");

                // Try as direct array
                List<AiProposalDto> proposals = objectMapper.readValue(
                        jsonResponse, new TypeReference<List<AiProposalDto>>() {});
                log.debug("Successfully parsed as direct array with {} proposals", proposals.size());

                // Log each proposal
                logProposals(proposals);

                return proposals;
            }
        } catch (Exception e) {
            log.error("Failed to parse AI suggestions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI suggestions", e);
        }
    }

    // Helper method to log individual proposals
    private void logProposals(List<AiProposalDto> proposals) {
        for (int i = 0; i < proposals.size(); i++) {
            AiProposalDto prop = proposals.get(i);
            log.debug("Proposal {}: action={}, nodeType={}, title={}, targetNodeId={}, parentNodeId={}",
                    i, prop.action(), prop.nodeType(), prop.title(),
                    prop.targetNodeId(), prop.parentNodeId());
        }
    }

    // Helper method to save large responses to file for inspection
    private void saveResponseToFile(String content, String filename) {
        try {
            Path debugDir = Paths.get("debug-logs");
            if (!Files.exists(debugDir)) {
                Files.createDirectory(debugDir);
            }
            Files.writeString(debugDir.resolve(filename), content);
            log.debug("Saved full AI response to {}", filename);
        } catch (IOException e) {
            log.warn("Could not save response to file: {}", e.getMessage());
        }
    }

    /**
     * Simple version that doesn't require course name - for backward compatibility
     */
    public List<AiProposalDto> analyzeContent(String newContent) {
        log.debug("analyzeContent called with content length: {} characters", newContent.length());

        // Get generic course structure from hierarchy service
        String courseContext = hierarchyService.generateLlmOutlineContext();

        log.debug("Course context length: {} characters", courseContext.length());

        String systemPrompt = """
            You are CourseCrafter AI, an expert system for educational content analysis.
            
            You will be given:
            1. The current structure and content of a course
            2. New content to be integrated into this course
            
            Analyze where and how this new content should be integrated. Return a JSON array of proposals following this schema:
            [{
              "targetNodeId": Long?,      // ID of existing node to modify, null for new nodes
              "parentNodeId": Long,       // Parent ID where to place new content
              "nodeType": "LECTURE|SECTION|TOPIC|SLIDE",
              "action": "ADD|UPDATE|DELETE",
              "title": String,            // Title for the node
              "nodeNumber": String?,      // Node number in the hierarchy (e.g., "1.2.3")
              "content": String,          // Content to add/replace
              "rationale": String,        // Explanation of why this change is needed
              "displayOrder": Integer?    // Suggested display order
            }]
            """;

        log.debug("System prompt: {}", systemPrompt);

        var response = chatClient.prompt()
                .system(systemPrompt)
                .user(String.format("""
                    # COURSE STRUCTURE
                    %s
                    
                    # NEW CONTENT TO INTEGRATE
                    %s
                    """, courseContext, newContent))
                .call();

        String jsonResponse = response.content();
        log.debug("AI analysis response raw: {}", jsonResponse);

        try {
            // Try to parse as different formats
            List<AiProposalDto> proposals;
            try {
                // First try to parse as AiProposalListDto (object with proposals field)
                AiProposalListDto proposalList = objectMapper.readValue(jsonResponse, AiProposalListDto.class);
                proposals = proposalList.proposals();
                log.debug("Successfully parsed response as AiProposalListDto");
            } catch (Exception e) {
                log.debug("Failed to parse as AiProposalListDto, trying as array: {}", e.getMessage());
                // If that fails, try to parse directly as array of AiProposalDto
                proposals = objectMapper.readValue(jsonResponse, new TypeReference<List<AiProposalDto>>() {});
                log.debug("Successfully parsed response as direct array");
            }

            log.debug("Parsed {} proposals from AI", proposals.size());
            for (int i = 0; i < proposals.size(); i++) {
                AiProposalDto prop = proposals.get(i);
                log.debug("Proposal {}: action={}, nodeType={}, title={}, contentLength={}",
                        i, prop.action(), prop.nodeType(), prop.title(),
                        prop.content() != null ? prop.content().length() : 0);
            }

            return proposals;
        } catch (Exception e) {
            log.error("Failed to parse AI suggestions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI suggestions", e);
        }
    }

    /**
     * Refines a specific content proposal for better integration
     */
    public AiProposalDto refineProposal(AiProposalDto proposal, String existingContent) {
        var refinementResponse = chatClient.prompt()
                .system("""
                        You are CourseCrafter AI, an expert in educational content creation.
                        
                        You're refining a content proposal to ensure it:
                        1. Uses consistent terminology and style
                        2. Has appropriate length and depth
                        3. Flows well with surrounding content
                        4. Follows best practices for educational material
                        
                        Return the refined content only, maintaining all markdown formatting.
                        """)
                .user("""
                        # ORIGINAL PROPOSAL
                        Action: %s
                        Title: %s
                        Node Type: %s
                        
                        # EXISTING CONTENT
                        ```
                        %s
                        ```
                        
                        # PROPOSED CONTENT
                        ```
                        %s
                        ```
                        
                        # RATIONALE
                        %s
                        """.formatted(
                        proposal.action(),
                        proposal.title(),
                        proposal.nodeType(),
                        existingContent != null ? existingContent : "N/A",
                        proposal.content(),
                        proposal.rationale()
                ))
                .call();

        String refinedContent = refinementResponse.content();

        // Extract content if wrapped in code blocks
        if (refinedContent.startsWith("```") && refinedContent.endsWith("```")) {
            refinedContent = refinedContent
                    .replaceFirst("```(?:markdown)?\\n", "")
                    .replaceFirst("\\n```$", "");
        }

        // Return updated proposal with refined content
        return new AiProposalDto(
                proposal.targetNodeId(),
                proposal.parentNodeId(),
                proposal.nodeType(),
                proposal.action(),
                proposal.title(),
                proposal.nodeNumber(),
                refinedContent,
                proposal.rationale(),
                proposal.displayOrder()
        );
    }

    /**
     * Course-specific version of content analysis
     * Takes the course name into account during analysis
     */
    public List<AiProposalDto> analyzeContentForCourse(String courseName, String newContent) {
        // Get course structure as formatted text for LLM context
        String courseContext = hierarchyService.generateLlmOutlineContextForCourse(courseName);

        // First, fetch the actual course node ID to use as fallback
        Long courseNodeId = contentNodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                .stream()
                .filter(node -> node.getTitle().equals(courseName))
                .map(ContentNode::getId)
                .findFirst()
                .orElse(1L); // Fallback to ID 1 if not found

        // Now include this in the system prompt
        var response = chatClient.prompt()
                .system("""
                You are CourseCrafter AI, an expert system for educational content analysis.
                
                You will be given:
                1. The current structure and content of a course named "%s"
                2. New content to be integrated into this course
                
                Analyze where and how this new content should be integrated. Focus specifically on how
                it fits within the existing structure of the "%s" course.
                
                IMPORTANT: All parentNodeId values MUST be valid existing IDs. If unsure, use %d as the course root ID.
                
                Return a JSON array of proposals following this schema:
                [{
                  "targetNodeId": Long?,      // ID of existing node to modify, null for new nodes
                  "parentNodeId": Long,       // Parent ID where to place new content (use %d if unsure)
                  "nodeType": "LECTURE|SECTION|TOPIC|SLIDE",
                  "action": "ADD|UPDATE|DELETE",
                  "title": String,            // Title for the node
                  "nodeNumber": String?,      // Node number in the hierarchy (e.g., "1.2.3")
                  "content": String,          // Content to add/replace
                  "rationale": String,        // Explanation of why this change is needed
                  "displayOrder": Integer?    // Suggested display order
                }]
                """.formatted(courseName, courseName, courseNodeId, courseNodeId))
                .user("""
                # %s COURSE STRUCTURE
                %s
                
                # NEW CONTENT TO INTEGRATE
                %s
                """.formatted(courseName, courseContext, newContent))
                .call();

        try {
            String jsonResponse = response.content();

            try {
                // First try to parse as AiProposalListDto (object with proposals field)
                AiProposalListDto proposalList = objectMapper.readValue(jsonResponse, AiProposalListDto.class);
                return proposalList.proposals();
            } catch (Exception e) {
                // If that fails, try to parse directly as array of AiProposalDto
                return objectMapper.readValue(jsonResponse, new TypeReference<List<AiProposalDto>>() {});
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI suggestions", e);
        }
    }
}