package com.coherentsolutions.coursecrafter.service.ai;

import com.coherentsolutions.coursecrafter.dto.SuggestionDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import com.coherentsolutions.coursecrafter.service.git.GitCliService;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdaterService {

    private final CourseContentRepository repo;
    private final GitCliService gitCli;

    private final Parser parser = Parser.builder().build();  // Flexmark core

    /**
     * Applies a batch of AI suggestions and returns the persisted rows.
     */
    @Transactional
    public List<CourseContent> apply(List<SuggestionDto> suggestions)
            throws IOException, InterruptedException {

        List<CourseContent> changed = new ArrayList<>();

        for (SuggestionDto s : suggestions) {
            CourseContent slide = repo.findById(s.slideId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown slide id: " + s.slideId()));

            String updated = switch (s.action()) {
                case ADD    -> insertMarkdown(slide.getMarkdown(), s.text());
                case UPDATE -> s.text();                    // full replace
                case DELETE -> "";                         // prune slide
            };

            slide.setMarkdown(updated);
            changed.add(slide);
        }

        repo.saveAll(changed);
        gitCli.commitAndPush("update-" + System.currentTimeMillis(),
                "Apply AI suggestions to " + changed.size() + " slides");

        return changed;
    }

    /* ────────────────────────────────────────────── internal helpers ─── */

    private String insertMarkdown(String original, String addition) {
        Document root = parser.parse(original);
        // trivial append; replace with smarter AST logic later
        return original + System.lineSeparator() + addition;
    }
}