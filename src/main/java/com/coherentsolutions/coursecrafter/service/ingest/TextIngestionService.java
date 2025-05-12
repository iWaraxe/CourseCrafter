// src/main/java/com/coherentsolutions/coursecrafter/service/ingest/TextIngestionService.java
package com.coherentsolutions.coursecrafter.service.ingest;

import com.coherentsolutions.coursecrafter.dto.SuggestionDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.ai.AnalyzerService;
import com.coherentsolutions.coursecrafter.service.ai.SummarizationService;
import com.coherentsolutions.coursecrafter.service.ai.UpdaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
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
        List<SuggestionDto> suggestions = analyzerService.suggest(cleanedMarkdown);

        if (suggestions.isEmpty()) {
            return Collections.emptyList();   // nothing to change
        }

        // 3) Apply the generated commands (DB + Git)
        return updaterService.apply(suggestions);
    }
}
