package com.coherentsolutions.coursecrafter.service.ai;

import com.coherentsolutions.coursecrafter.dto.EnhancedProposalDto;
import com.coherentsolutions.coursecrafter.dto.ProposalDto;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import com.coherentsolutions.coursecrafter.service.git.GitCliService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnhancedUpdaterService {

    private final CourseContentRepository repo;
    private final GitCliService gitCli;

    /**
     * Applies a batch of enhanced proposals
     */
    @Transactional
    public List<CourseContent> applyProposals(List<EnhancedProposalDto> proposals, String branchName)
            throws IOException, InterruptedException {

        List<CourseContent> changed = new ArrayList<>();

        for (EnhancedProposalDto proposal : proposals) {
            switch (proposal.action()) {
                case ADD -> {
                    CourseContent newContent = createSlide(
                            proposal.sectionId(),  // Important: use sectionId as parent
                            proposal.slideTitle(),
                            proposal.updatedContent(),
                            generatePath(proposal.lectureId(), proposal.sectionId())
                    );
                    changed.add(newContent);
                }
                case UPDATE -> {
                    CourseContent updated = updateSlide(
                            proposal.slideId(),
                            proposal.slideTitle(),
                            proposal.updatedContent()
                    );
                    changed.add(updated);
                }
                case DELETE -> deleteSlide(proposal.slideId());
            }
        }

        if (!changed.isEmpty()) {
            repo.saveAll(changed);
            gitCli.commitAndPush(
                    branchName,
                    "Apply AI-generated course updates (" + changed.size() + " changes)"
            );
        }

        return changed;
    }

    private CourseContent createSlide(Long parentId, String title, String markdown, String path) {
        CourseContent content = CourseContent.builder()
                .parentId(parentId)  // Set the parentId (section)
                .level("SLIDE")
                .title(title)
                .markdown(markdown)
                .path(path)
                .build();

        return repo.save(content);
    }

    private CourseContent updateSlide(Long slideId, String title, String markdown) {
        CourseContent slide = repo.findById(slideId)
                .orElseThrow(() -> new IllegalArgumentException("Slide not found: " + slideId));

        slide.setTitle(title);
        slide.setMarkdown(markdown);

        return repo.save(slide);
    }

    private void deleteSlide(Long slideId) {
        repo.deleteById(slideId);
    }

    private String generatePath(Long lectureId, Long sectionId) {
        return String.format("Lecture%d/Section%d/Slide-%s",
                lectureId, sectionId, UUID.randomUUID().toString().substring(0, 8));
    }
}