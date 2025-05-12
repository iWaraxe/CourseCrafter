package com.coherentsolutions.coursecrafter.service.ai;

import com.coherentsolutions.coursecrafter.dto.ProposalDto;
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

import static com.coherentsolutions.coursecrafter.dto.ProposalDto.Action.*;

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
    public List<CourseContent> apply(List<ProposalDto> proposals)
            throws IOException, InterruptedException {

        List<CourseContent> changed = new ArrayList<>();

        for (ProposalDto p : proposals) {
            switch (p.action()) {
                case ADD -> {
                    CourseContent created = insertNewSlide(p);
                    changed.add(created);
                }
                case UPDATE -> {
                    CourseContent updated = updateSlide(p);
                    changed.add(updated);
                }
                case DELETE -> deleteSlide(p.slideId());
            }
        }

        if (!changed.isEmpty()) {
            repo.saveAll(changed);
            gitCli.commitAndPush(
                    "update-" + System.currentTimeMillis(),
                    "Apply AI proposals to " + changed.size() + " slides");
        }

        return changed;
    }

    /* ────────────────────────────────────────────── internal helpers ─── */

    private String insertMarkdown(String original, String addition) {
        Document root = parser.parse(original);
        // trivial append; replace with smarter AST logic later
        return original + System.lineSeparator() + addition;
    }

    private CourseContent insertNewSlide(ProposalDto p) {
        CourseContent cc = CourseContent.builder()
                .parentId(p.lectureId())           // or section ID
                .level("SLIDE")
                .title("New slide")
                .markdown(p.text())
                .path(resolvePath(p))              // e.g. Lecture1/…
                .build();
        return repo.save(cc);
    }

    private void deleteSlide(Long id) { repo.deleteById(id); }

    private CourseContent updateSlide(ProposalDto p) {
        CourseContent slide = repo.findById(p.slideId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown slide id: " + p.slideId()));
        slide.setMarkdown(p.text());
        return repo.save(slide);
    }

    private String resolvePath(ProposalDto p) {
        // naive path generator – customise later if needed
        return "Lecture" + p.lectureId() + "/slide-" + (p.slideId() != null ? p.slideId() : "new");
    }
}