package com.coherentsolutions.coursecrafter.service.ingest;

import com.coherentsolutions.coursecrafter.dto.AiProposalDto;
import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.ai.EnhancedAnalyzerService;
import com.coherentsolutions.coursecrafter.service.ai.EnhancedUpdaterService;
import com.coherentsolutions.coursecrafter.service.ai.SummarizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnhancedTextIngestionService {

    private final SummarizationService summarizationService;
    private final EnhancedAnalyzerService analyzerService;
    private final EnhancedUpdaterService updaterService;

    /**
     * Complete process for ingesting and integrating new content
     */
    public List<ContentNode> processContent(String rawContent) throws IOException, InterruptedException {
        // 1. Clean and summarize the content
        String cleanedContent = summarizationService.summarize(rawContent);

        // 2. Analyze content and generate proposals
        List<AiProposalDto> initialProposals = analyzerService.analyzeContent(cleanedContent);

        if (initialProposals.isEmpty()) {
            return List.of(); // Nothing to do
        }

        // 3. Refine each proposal for better quality
        List<AiProposalDto> refinedProposals = initialProposals.stream()
                .map(proposal -> analyzerService.refineProposal(proposal, null))
                .collect(Collectors.toList());

        // 4. Apply the proposals to create/update content
        return updaterService.applyProposals(refinedProposals);
    }

    /**
     * Process content updates for a specific course
     * This method takes courseName into account during analysis
     */
    public List<CourseContent> processContentUpdate(String courseName, String content)
            throws IOException, InterruptedException {

        // 1. Clean and summarize the content
        String cleanedContent = summarizationService.summarize(content);

        // 2. Analyze content and generate proposals - the analyzer should use the courseName to contextualize
        List<AiProposalDto> initialProposals = analyzerService.analyzeContentForCourse(courseName, cleanedContent);

        if (initialProposals.isEmpty()) {
            return List.of(); // Nothing to do
        }

        // 3. Refine each proposal for better quality
        List<AiProposalDto> refinedProposals = initialProposals.stream()
                .map(proposal -> analyzerService.refineProposal(proposal, null))
                .collect(Collectors.toList());

        // 4. Apply the proposals to create/update content
        List<ContentNode> updatedNodes = updaterService.applyProposals(refinedProposals);

        // 5. Convert ContentNode objects to CourseContent objects (temporary during migration)
        return convertToCourseContent(updatedNodes);
    }

    /**
     * Temporary helper method to convert ContentNode objects to CourseContent objects during migration
     */
    private List<CourseContent> convertToCourseContent(List<ContentNode> nodes) {
        List<CourseContent> results = new ArrayList<>();

        for (ContentNode node : nodes) {
            CourseContent cc = new CourseContent();
            cc.setId(node.getId());
            cc.setTitle(node.getTitle());
            cc.setLevel(node.getNodeType().toString());
            cc.setPath(node.getPath());
            // Set other fields as needed

            results.add(cc);
        }

        return results;
    }
}