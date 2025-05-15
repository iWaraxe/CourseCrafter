package com.coherentsolutions.coursecrafter.application.service;

import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedAnalyzerService;
import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedUpdaterService;
import com.coherentsolutions.coursecrafter.application.api.ai.SummarizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
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
     * Process content updates for a specific course with enhanced context
     */
    public List<ContentNode> processContentUpdate(
            String courseName,
            String content,
            String audience,
            LocalDate reportDate) throws IOException, InterruptedException {

        // 1. Clean and summarize the content with enhanced context
        String cleanedContent = summarizationService.summarize(content, courseName, audience, reportDate);

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
        return updaterService.applyProposals(refinedProposals);
    }

    /**
     * Enhanced content processing with additional context
     */
    public List<ContentNode> processContent(
            String rawContent,
            String courseName,
            String audience,
            LocalDate reportDate) throws IOException, InterruptedException {

        // Use enhanced summarization if context is provided
        String cleanedContent;
        if (courseName != null && audience != null && reportDate != null) {
            cleanedContent = summarizationService.summarize(rawContent, courseName, audience, reportDate);
        } else {
            cleanedContent = summarizationService.summarize(rawContent);
        }

        // Use course-specific analysis if course name is provided
        List<AiProposalDto> initialProposals;
        if (courseName != null) {
            initialProposals = analyzerService.analyzeContentForCourse(courseName, cleanedContent);
        } else {
            initialProposals = analyzerService.analyzeContent(cleanedContent);
        }

        if (initialProposals.isEmpty()) {
            return List.of(); // Nothing to do
        }

        // Refine each proposal for better quality
        List<AiProposalDto> refinedProposals = initialProposals.stream()
                .map(proposal -> analyzerService.refineProposal(proposal, null))
                .collect(Collectors.toList());

        // Apply the proposals to create/update content
        return updaterService.applyProposals(refinedProposals);
    }

    /**
     * Original method for backward compatibility
     */
    public List<ContentNode> processContent(String rawContent) throws IOException, InterruptedException {
        return processContent(rawContent, null, null, null);
    }
}