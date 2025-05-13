package com.coherentsolutions.coursecrafter.service.ai;

import com.coherentsolutions.coursecrafter.dto.EnhancedProposalDto;
import com.coherentsolutions.coursecrafter.dto.ProposalDto;
import com.coherentsolutions.coursecrafter.service.CourseStructureService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnhancedAnalyzerService {

    private final CourseStructureService courseStructureService;
    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * First stage analysis: Determine which parts of the course need updates
     * @param courseName name of the course
     * @param newContent proposed new content
     * @return list of proposed updates with specific locations
     */
    public List<EnhancedProposalDto> analyzeContentPlacement(String courseName, String newContent) {
        // Get the course outline as text
        String courseOutline = courseStructureService.getCourseOutlineText(courseName);

        // Prompt the LLM to analyze where the new content should be placed
        var result = chatClient.prompt()
                .system("""
                        You are CourseCrafter AI, an educational content analysis expert.
                        
                        You will be given:
                        1. A structured outline of an existing course with lectures, sections, and slides
                        2. New content that needs to be integrated into the course
                        
                        Your task is to identify exactly where this new content should be placed:
                        
                        - If content should be added as a new slide, specify the parent lecture and section
                        - If content should update an existing slide, identify that specific slide
                        - If content should replace an existing slide, mark it for deletion and suggest a new slide
                        
                        Return a JSON array of update proposals, following this schema:
                        [{
                          "lectureId": number,        // Parent lecture ID
                          "lectureTitle": string,     // Title of the lecture
                          "sectionId": number,        // Parent section ID
                          "sectionTitle": string,     // Title of the section
                          "slideId": number | null,   // Slide ID (null for new slides)
                          "slideTitle": string,       // Title for the slide
                          "action": "ADD"|"UPDATE"|"DELETE", // Type of change
                          "originalContent": string,  // Original content (for UPDATE/DELETE)
                          "updatedContent": string,   // New content (for ADD/UPDATE)
                          "rationale": string,        // Why this change is needed
                          "id": string               // Unique ID for tracking
                        }]
                        
                        Be strategic in your analysis. Look for:
                        1. Content gaps that need to be filled
                        2. Outdated content that needs updating
                        3. Logical placement in the course flow
                        
                        Pay attention to the IDs in the course outline - they're critical for correct placement.
                        """)
                .user("""
                        # COURSE OUTLINE
                        %s
                        
                        # NEW CONTENT TO INTEGRATE
                        %s
                        """.formatted(courseOutline, newContent))
                .call();

        try {
            String json = result.content();
            return mapper.readValue(json, new TypeReference<List<EnhancedProposalDto>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    /**
     * Second stage analysis: Generate specific content for updates
     * @param proposal initial proposal with placement information
     * @return refined proposal with finalized content
     */
    public EnhancedProposalDto refineContent(EnhancedProposalDto proposal) {
        // This stage is for refining the actual content of the proposed change
        var result = chatClient.prompt()
                .system("""
                        You are CourseCrafter AI, an educational content specialist.
                        
                        You're now refining a proposed course update. You will be given:
                        1. The original slide content (if updating/replacing)
                        2. The initially proposed content update
                        
                        Your task is to create the final, polished version of this content that:
                        - Uses clear, concise educational language
                        - Maintains consistent formatting with the rest of the course
                        - Integrates well with surrounding content
                        - Follows best practices for slide design
                        
                        Return ONLY the finalized Markdown content.
                        """)
                .user("""
                        # CONTEXT
                        Lecture: %s (ID: %d)
                        Section: %s (ID: %d)
                        Action: %s
                        
                        # ORIGINAL CONTENT
                        ```markdown
                        %s
                        ```
                        
                        # PROPOSED UPDATE
                        ```markdown
                        %s
                        ```
                        
                        # RATIONALE FOR CHANGE
                        %s
                        """.formatted(
                        proposal.lectureTitle(), proposal.lectureId(),
                        proposal.sectionTitle(), proposal.sectionId(),
                        proposal.action(),
                        proposal.originalContent() != null ? proposal.originalContent() : "N/A",
                        proposal.updatedContent(),
                        proposal.rationale()
                ))
                .call();

        String refinedContent = result.content().trim();

        // If the response has markdown code blocks, extract just the content
        if (refinedContent.startsWith("```") && refinedContent.endsWith("```")) {
            refinedContent = refinedContent
                    .replaceFirst("```(?:markdown)?\\n", "")
                    .replaceFirst("\\n```$", "");
        }

        // Return updated proposal with refined content
        return new EnhancedProposalDto(
                proposal.lectureId(),
                proposal.lectureTitle(),
                proposal.sectionId(),
                proposal.sectionTitle(),
                proposal.slideId(),
                proposal.slideTitle(),
                proposal.action(),
                proposal.originalContent(),
                refinedContent,
                proposal.rationale(),
                proposal.id()
        );
    }
}