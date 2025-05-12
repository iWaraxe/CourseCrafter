package com.coherentsolutions.coursecrafter.service.ingest;

import com.coherentsolutions.coursecrafter.dto.ProposalDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.ai.AnalyzerService;
import com.coherentsolutions.coursecrafter.service.ai.SummarizationService;
import com.coherentsolutions.coursecrafter.service.ai.UpdaterService;
import com.coherentsolutions.coursecrafter.service.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Orchestrates the full *text‑ingestion* pipeline:
 *
 * <pre>
 * raw upload  →  SummarizationService   →  AnalyzerService
 *             →  UpdaterService         →  Git commit
 * </pre>
 *
 * Once the {@link UpdaterService} finishes, the updated / newly‑created
 * {@link CourseContent} rows are returned to the caller.
 */
@Service
@RequiredArgsConstructor
public class TextIngestionService {

    private final SummarizationService summarizationService;
    private final AnalyzerService analyzerService;
    private final UpdaterService updaterService;
    private final GitCliService gitCli;

    /**
     * Executes the happy‑path flow for a raw Markdown upload.
     *
     * @param rawMarkdown text coming from the REST layer
     * @return the list of {@link CourseContent} entities that were inserted or
     *         modified as a result of this ingestion. Empty if no changes were
     *         necessary.
     */
    public List<CourseContent> ingest(String rawMarkdown)
            throws IOException, InterruptedException {

        // 1) Normalise & condense the instructor's input
        String cleanedMarkdown = summarizationService.summarize(rawMarkdown);

        // 2) Ask the LLM what to do with this new material
        List<ProposalDto> proposals = analyzerService.propose(cleanedMarkdown);

        if (proposals.isEmpty()) {
            return List.of();   // nothing to change
        }

        // 3) Apply the generated commands (DB + Git)
        return updaterService.apply(proposals);
    }

    public List<CourseContent> reviewAndApply(String raw)
            throws IOException, InterruptedException {

        String cleaned = summarizationService.summarize(raw);
        List<ProposalDto> proposals = analyzerService.propose(cleaned);

        if (proposals.isEmpty()) return List.of();

        List<CourseContent> changed = updaterService.apply(proposals);

        gitCli.createPr(
                "update-" + System.currentTimeMillis(),
                "Course updates (" + changed.size() + " slides)",
                "Automated AI proposals"
        );
        return changed;
    }
}
