package com.coherentsolutions.coursecrafter.application.service;

import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedAnalyzerService;
import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedUpdaterService;
import com.coherentsolutions.coursecrafter.application.api.ai.SummarizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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

        log.info("Processing content update for course: {}, audience: {}, reportDate: {}",
                courseName, audience, reportDate);
        log.debug("Raw content length: {} characters", content.length());

        // 1. Clean and summarize the content with enhanced context
        log.debug("Step 1: Summarizing content...");
        String cleanedContent = summarizationService.summarize(content, courseName, audience, reportDate);
        log.debug("Summarized content length: {} characters", cleanedContent.length());

        // 2. Analyze content and generate proposals
        log.debug("Step 2: Analyzing content to generate proposals...");
        List<AiProposalDto> initialProposals = analyzerService.analyzeContentForCourse(courseName, cleanedContent);
        log.debug("Generated {} initial proposals", initialProposals.size());

        if (initialProposals.isEmpty()) {
            log.info("No proposals generated, nothing to update");
            return List.of(); // Nothing to do
        }

        // 3. Refine each proposal for better quality
        log.debug("Step 3: Refining proposals...");
        List<AiProposalDto> refinedProposals = initialProposals.stream()
                .map(proposal -> {
                    log.debug("Refining proposal: {}", proposal.title());
                    return analyzerService.refineProposal(proposal, null);
                })
                .collect(Collectors.toList());
        log.debug("Refined {} proposals", refinedProposals.size());

        // 4. Apply the proposals to create/update content
        log.debug("Step 4: Applying proposals to update content...");
        List<ContentNode> updatedNodes = updaterService.applyProposals(refinedProposals);
        log.info("Updated {} content nodes", updatedNodes.size());

        return updatedNodes;
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