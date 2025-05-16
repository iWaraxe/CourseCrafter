package com.coherentsolutions.coursecrafter.presentation.controller;

import com.coherentsolutions.coursecrafter.application.api.ai.EnhancedUpdaterService;
import com.coherentsolutions.coursecrafter.presentation.dto.ai.AiProposalDto;
import com.coherentsolutions.coursecrafter.presentation.dto.content.CourseUpdateRequest;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import com.coherentsolutions.coursecrafter.application.service.EnhancedTextIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseUpdateController {

    private final ContentHierarchyService contentHierarchyService;
    private final EnhancedTextIngestionService ingestionService;
    private final EnhancedUpdaterService enhancedUpdaterService;

    /**
     * Get course structure in hierarchical format
     */
    @GetMapping("/{courseName}/structure")
    public ResponseEntity<?> getCourseStructure(@PathVariable String courseName) {
        return ResponseEntity.ok(contentHierarchyService.generateDetailedOutlineContext(courseName));
    }

    /**
     * Process new content and integrate it into the course
     */
    @PostMapping("/{courseName}/update")
    public ResponseEntity<?> updateCourse(
            @PathVariable String courseName,
            @RequestBody CourseUpdateRequest request) throws IOException, InterruptedException {

        // Use the enhanced text ingestion service to process the content
        String content = request.content();
        String audience = request.audience();
        LocalDate reportDate = request.reportDate();

        // First clean and summarize the content
        String cleanedContent = ingestionService.summarizeContent(content, courseName, audience, reportDate);

        // Analyze content and generate proposals
        List<AiProposalDto> proposals = ingestionService.analyzeContent(cleanedContent, courseName);

        if (proposals.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No updates needed. Content is already covered in the course."
            ));
        }

        // Create a PR with the proposals (without database updates)
        String prUrl = enhancedUpdaterService.createProposalPR(proposals);

        return ResponseEntity.ok(Map.of(
                "message", String.format("%d proposed changes applied and Pull Request created", proposals.size()),
                "pr_url", prUrl,
                "note", "Changes will be applied to the database after PR approval"
        ));
    }
}