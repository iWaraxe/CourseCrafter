package com.coherentsolutions.coursecrafter.controller;

import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentCreateRequest;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentTreeDto;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentUpdateRequest;
import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentHierarchyService;
import com.coherentsolutions.coursecrafter.domain.content.service.ContentNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentNodeController {

    private final ContentNodeService nodeService;
    private final ContentHierarchyService hierarchyService;

    @GetMapping("/tree")
    public ResponseEntity<ContentTreeDto> getContentTree() {
        return ResponseEntity.ok(hierarchyService.getContentTree());
    }

    @GetMapping("/outline")
    public ResponseEntity<String> getOutline() {
        return ResponseEntity.ok(hierarchyService.generateOutline());
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<?> getNode(@PathVariable Long nodeId) {
        return nodeService.getNodeWithLatestContent(nodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createNode(@RequestBody ContentCreateRequest request)
            throws IOException, InterruptedException {

        ContentNode parent = null;
        if (request.parentId() != null) {
            parent = new ContentNode();
            parent.setId(request.parentId());
        }

        ContentNode node = ContentNode.builder()
                .parent(parent)
                .nodeType(ContentNode.NodeType.valueOf(request.nodeType()))
                .title(request.title())
                .description(request.description())
                .nodeNumber(request.nodeNumber())
                .displayOrder(request.displayOrder())
                .build();

        ContentNode created = nodeService.createNode(
                node,
                request.content(),
                "Create new " + request.nodeType() + ": " + request.title());

        return ResponseEntity.ok(created);
    }

    @PutMapping("/{nodeId}")
    public ResponseEntity<?> updateNode(
            @PathVariable Long nodeId,
            @RequestBody ContentUpdateRequest request) throws IOException, InterruptedException {

        ContentNode updated = nodeService.updateNode(
                nodeId,
                request.content(),
                "Update content for node: " + nodeId);

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<?> deleteNode(@PathVariable Long nodeId) throws IOException, InterruptedException {
        nodeService.deleteNode(nodeId, "Delete node: " + nodeId);
        return ResponseEntity.ok(Map.of("message", "Node deleted successfully"));
    }
}