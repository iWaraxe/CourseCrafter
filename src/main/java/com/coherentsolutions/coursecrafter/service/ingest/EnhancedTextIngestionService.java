package com.coherentsolutions.coursecrafter.service.ingest;

import com.coherentsolutions.coursecrafter.dto.EnhancedProposalDto;
import com.coherentsolutions.coursecrafter.dto.ProposalDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.ai.EnhancedAnalyzerService;
import com.coherentsolutions.coursecrafter.service.ai.EnhancedUpdaterService;
import com.coherentsolutions.coursecrafter.service.ai.SummarizationService;
import com.coherentsolutions.coursecrafter.service.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnhancedTextIngestionService {

    private final SummarizationService summarizationService;
    private final EnhancedAnalyzerService analyzerService;
    private final EnhancedUpdaterService updaterService;
    private final GitCliService gitCli;

    /**
     * Executes the complete course update workflow
     *
     * @param courseName name of the course to update
     * @param rawContent raw content to integrate
     * @return list of created/updated content entries
     */
    public List<CourseContent> processContentUpdate(String courseName, String rawContent)
            throws IOException, InterruptedException {

        // 1. Create a unique branch name with timestamp
        String branchName = generateBranchName();

        // 2. Summarize/clean the raw content
        String cleanedContent = summarizationService.summarize(rawContent);

        // 3. First-pass analysis: determine where content should be placed
        List<EnhancedProposalDto> initialProposals =
                analyzerService.analyzeContentPlacement(courseName, cleanedContent);

        if (initialProposals.isEmpty()) {
            return List.of(); // Nothing to change
        }

        // 4. Second-pass analysis: refine each piece of content
        List<EnhancedProposalDto> refinedProposals = initialProposals.stream()
                .map(analyzerService::refineContent)
                .collect(Collectors.toList());

        // 5. Apply the changes to the database and commit to Git
        List<CourseContent> updatedContent =
                updaterService.applyProposals(refinedProposals, branchName);

        // 6. Create a pull request
        if (!updatedContent.isEmpty()) {
            String prTitle = "Course updates: " + courseName + " (" + updatedContent.size() + " changes)";
            String prBody = generatePrDescription(refinedProposals);

            gitCli.createPr(branchName, prTitle, prBody);
        }

        return updatedContent;
    }

    private String generateBranchName() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
        return "course-update-" + LocalDateTime.now().format(formatter);
    }

    private String generatePrDescription(List<EnhancedProposalDto> proposals) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI-Generated Course Updates\n\n");
        sb.append("This PR contains the following proposed updates:\n\n");

        int counter = 1;
        for (EnhancedProposalDto proposal : proposals) {
            sb.append("## Change ").append(counter++).append(": ");
            sb.append(proposal.action()).append(" - ").append(proposal.slideTitle()).append("\n\n");
            sb.append("**Location:** Lecture: ").append(proposal.lectureTitle());
            sb.append(", Section: ").append(proposal.sectionTitle()).append("\n\n");
            sb.append("**Rationale:** ").append(proposal.rationale()).append("\n\n");

            if (proposal.action() != ProposalDto.Action.DELETE) {
                sb.append("**Preview:**\n```markdown\n")
                        .append(proposal.updatedContent().substring(0,
                                Math.min(200, proposal.updatedContent().length())))
                        .append("...\n```\n\n");
            }

            sb.append("---\n\n");
        }

        sb.append("Please review these changes and provide feedback using comments.\n");
        return sb.toString();
    }
}