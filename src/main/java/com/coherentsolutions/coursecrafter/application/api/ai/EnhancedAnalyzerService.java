package com.coherentsolutions.coursecrafter.application.api.ai;

import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalListDto;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnhancedAnalyzerService {

    private final ContentHierarchyService hierarchyService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Analyzes new content and generates proposals for integrating it
     * into the existing content structure
     */
    public List<AiProposalDto> analyzeContent(String cleanedContent, String courseName) {
        // Get course structure as formatted text for LLM context
        String courseContext = hierarchyService.generateDetailedOutlineContext(courseName);

        var response = chatClient.prompt()
                .system("""
                You are CourseCrafter AI, an expert system for educational content analysis.
                
                You will be given:
                1. The current detailed structure of the "%s" course
                2. New content to be integrated into this course
                
                Analyze specifically where and how this new content should be integrated, considering:
                - The appropriate lecture, section, topic, and slide placement
                - Whether to create new nodes or update existing ones
                - The type of content (script, visual, notes, or demonstration)
                - The teaching level and target audience
                
                Return a JSON array of precise proposals following this schema:
                [{
                  "targetNodeId": Long?,      // ID of existing node to modify, null for new nodes
                  "parentNodeId": Long,       // Parent ID where to place new content
                  "nodeType": "LECTURE|SECTION|TOPIC|SLIDE",
                  "action": "ADD|UPDATE|DELETE",
                  "componentType": "SCRIPT|VISUAL|NOTES|DEMONSTRATION",  // For slides only
                  "title": String,            // Title for the node
                  "nodeNumber": String?,      // Node number in the hierarchy (e.g., "1.2.3")
                  "content": String,          // Content to add/replace
                  "rationale": String,        // Explanation of why this change is needed
                  "displayOrder": Integer?    // Suggested display order
                }]
                
                Be extremely precise about placement, teaching level, and component type.
                """.formatted(courseName))
                .user("""
                # COURSE STRUCTURE
                %s
                
                # NEW CONTENT TO INTEGRATE
                %s
                """.formatted(courseContext, cleanedContent))
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

    /**
     * Simple version that doesn't require course name - for backward compatibility
     */
    public List<AiProposalDto> analyzeContent(String newContent) {
        // Get generic course structure from hierarchy service
        String courseContext = hierarchyService.generateLlmOutlineContext();

        var response = chatClient.prompt()
                .system("""
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
                    
                    For new nodes (targetNodeId = null), you must specify:
                    - The parentNodeId where it should be placed
                    - The nodeType it should be
                    - A meaningful title
                    - The content
                    
                    For updates (targetNodeId != null), provide:
                    - The targetNodeId to modify
                    - New/modified content
                    
                    Be strategic and thoughtful about:
                    1. Where content logically fits in the course structure
                    2. Whether to create new nodes or update existing ones
                    3. Maintaining consistent style and format with existing content
                    """)
                .user("""
                    # COURSE STRUCTURE
                    %s
                    
                    # NEW CONTENT TO INTEGRATE
                    %s
                    """.formatted(courseContext, newContent))
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

        var response = chatClient.prompt()
                .system("""
                    You are CourseCrafter AI, an expert system for educational content analysis.
                    
                    You will be given:
                    1. The current structure and content of a course named "%s"
                    2. New content to be integrated into this course
                    
                    Analyze where and how this new content should be integrated. Focus specifically on how
                    it fits within the existing structure of the "%s" course.
                    
                    Return a JSON array of proposals following this schema:
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
                    """.formatted(courseName, courseName))
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