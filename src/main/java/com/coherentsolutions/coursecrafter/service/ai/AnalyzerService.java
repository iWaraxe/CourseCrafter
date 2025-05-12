// src/main/java/com/coherentsolutions/coursecrafter/service/ai/AnalyzerService.java
package com.coherentsolutions.coursecrafter.service.ai;

import com.coherentsolutions.coursecrafter.dto.ProposalDto;
import com.coherentsolutions.coursecrafter.dto.ProposalList;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compares a new Markdown upload against the existing course database and
 * asks the LLM to emit a list of ADD / UPDATE / DELETE suggestions.
 *
 * This version uses a plain JPA query (no vector-store RAG yet). It builds a
 * compact textual context from every slide in the DB, feeds it—along with the
 * newly‑uploaded material—to ChatGPT, and expects back a JSON array that
 * conforms to {@link ProposalDto}.
 */
@Service
@RequiredArgsConstructor
public class AnalyzerService {

    private final CourseContentRepository repo;
    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param newMarkdown raw or pre‑normalised Markdown supplied by the user
     * @return list of suggested content operations
     */
    public List<ProposalDto> propose(String newMarkdown) {

        // 1) Build a compact context from all existing slides
        String dbContext = repo.findAll().stream()
                .map(slide -> "### ID " + slide.getId() +
                              " (" + slide.getPath() + ")\n" +
                              slide.getMarkdown())
                .collect(Collectors.joining("\n\n"));

        // 2) Ask the model to produce change commands
        var proposalList = chatClient.prompt()
                .system("""
                        You are CourseCrafter AI. Compare NEW course material
                        with the EXISTING corpus and output a JSON array of
                        change commands matching this schema:

                        [{
                          "lectureId" : Long,
                          "slideId"   : Long,
                          "action"    : "ADD|UPDATE|DELETE",
                          "text"      : "markdown",
                          "id"        : "string"
                        }]
                        """)
                .user("""
                        NEW SLIDES
                        ----------
                        %s
                        
                        EXISTING SLIDES FROM DATABASE
                        -----------------------------
                        %s
                        """.formatted(newMarkdown, dbContext))
                .call()
                .entity(ProposalList.class);

        return proposalList.proposals();
    }
}