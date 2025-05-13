package com.coherentsolutions.coursecrafter.controller;

import com.coherentsolutions.coursecrafter.dto.CourseUpdateRequest;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.service.ContentHierarchyService;
import com.coherentsolutions.coursecrafter.service.ingest.EnhancedTextIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseUpdateController {

    private final ContentHierarchyService contentHierarchyService; // Updated service name
    private final EnhancedTextIngestionService ingestionService;

    /**
     * Get course structure in hierarchical format
     */
    @GetMapping("/{courseName}/structure")
    public ResponseEntity<?> getCourseStructure(@PathVariable String courseName) {
        // This method now uses the ContentHierarchyService instead of CourseStructureService
        return ResponseEntity.ok(contentHierarchyService.generateLlmOutlineContextForCourse(courseName));
    }

    /**
     * Process new content and integrate it into the course
     */
    @PostMapping("/{courseName}/update")
    public ResponseEntity<?> updateCourse(
            @PathVariable String courseName,
            @RequestBody CourseUpdateRequest request) throws IOException, InterruptedException {

        List<CourseContent> updatedContent =
                ingestionService.processContentUpdate(courseName, request.content());

        if (updatedContent.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No updates needed. Content is already covered in the course."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", String.format("%d changes applied and Pull Request created", updatedContent.size()),
                "changes", updatedContent
        ));
    }
}
