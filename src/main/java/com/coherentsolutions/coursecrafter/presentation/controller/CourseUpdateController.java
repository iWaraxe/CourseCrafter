package com.coherentsolutions.coursecrafter.presentation.controller;

import com.coherentsolutions.coursecrafter.presentation.dto.content.CourseUpdateRequest;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import com.coherentsolutions.coursecrafter.application.service.EnhancedTextIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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

        List<ContentNode> updatedNodes = ingestionService.processContentUpdate(
                courseName,
                request.content(),
                request.audience(),
                request.reportDate());

        if (updatedNodes.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No updates needed. Content is already covered in the course."
            ));
        }

        // Map nodes to a simpler presentation structure
        List<Map<String, Object>> changes = updatedNodes.stream()
                .map(node -> {
                    Map<String, Object> nodeInfo = new HashMap<>();
                    nodeInfo.put("id", node.getId());
                    nodeInfo.put("title", node.getTitle());
                    nodeInfo.put("type", node.getNodeType());
                    nodeInfo.put("path", node.getPath());
                    return nodeInfo;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "message", String.format("%d changes applied and Pull Request created", updatedNodes.size()),
                "changes", changes
        ));
    }
}