package com.coherentsolutions.coursecrafter.presentation.controller;

import com.coherentsolutions.coursecrafter.presentation.dto.content.IngestionRequest;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
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
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class ContentIngestionController {

    private final EnhancedTextIngestionService ingestionService;

    @PostMapping("/content")
    public ResponseEntity<?> ingestContent(@RequestBody IngestionRequest request)
            throws IOException, InterruptedException {

        List<ContentNode> updatedNodes;

        // Use enhanced method if additional context is provided
        if (request.courseName() != null && request.audience() != null && request.reportDate() != null) {
            updatedNodes = ingestionService.processContent(
                    request.payload(),
                    request.courseName(),
                    request.audience(),
                    request.reportDate()
            );
        } else {
            // Use the original method for backward compatibility
            updatedNodes = ingestionService.processContent(request.payload());
        }

        if (updatedNodes.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No updates needed. Content is already covered or not relevant."
            ));
        }

        // Map nodes to a more presentation-friendly structure
        List<Map<String, Object>> nodeInfo = updatedNodes.stream()
                .map(node -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", node.getId());
                    info.put("title", node.getTitle());
                    info.put("type", node.getNodeType().toString());
                    info.put("path", node.getPath());
                    return info;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "message", "Created/updated " + updatedNodes.size() + " content nodes",
                "nodes", nodeInfo
        ));
    }
}