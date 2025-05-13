package com.coherentsolutions.coursecrafter.controller;

import com.coherentsolutions.coursecrafter.presentation.dto.content.IngestionRequest;
import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.application.service.EnhancedTextIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class ContentIngestionController {

    private final EnhancedTextIngestionService ingestionService;

    @PostMapping("/content")
    public ResponseEntity<?> ingestContent(@RequestBody IngestionRequest request)
            throws IOException, InterruptedException {

        List<ContentNode> updatedNodes = ingestionService.processContent(request.payload());

        if (updatedNodes.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No updates needed. Content is already covered or not relevant."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Created/updated " + updatedNodes.size() + " content nodes",
                "nodes", updatedNodes
        ));
    }
}