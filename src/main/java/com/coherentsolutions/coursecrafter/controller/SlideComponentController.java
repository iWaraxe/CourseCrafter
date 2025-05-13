package com.coherentsolutions.coursecrafter.controller;

import com.coherentsolutions.coursecrafter.presentation.dto.slide.ComponentCreateRequest;
import com.coherentsolutions.coursecrafter.presentation.dto.slide.ComponentUpdateRequest;
import com.coherentsolutions.coursecrafter.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/slides/{slideId}/components")
@RequiredArgsConstructor
public class SlideComponentController {

    private final SlideComponentService componentService;

    @GetMapping
    public ResponseEntity<List<SlideComponent>> getComponents(@PathVariable Long slideId) {
        return ResponseEntity.ok(componentService.getComponentsForSlide(slideId));
    }

    @PostMapping
    public ResponseEntity<SlideComponent> createComponent(
            @PathVariable Long slideId,
            @RequestBody ComponentCreateRequest request) {

        SlideComponent created = componentService.createComponent(
                slideId,
                SlideComponent.ComponentType.valueOf(request.componentType()),
                request.content());

        return ResponseEntity.ok(created);
    }

    @PutMapping("/{componentId}")
    public ResponseEntity<?> updateComponent(
            @PathVariable Long slideId,
            @PathVariable Long componentId,
            @RequestBody ComponentUpdateRequest request) {

        return componentService.updateComponent(componentId, request.content())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{componentId}")
    public ResponseEntity<?> deleteComponent(
            @PathVariable Long slideId,
            @PathVariable Long componentId) {

        boolean deleted = componentService.deleteComponent(componentId);

        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Component deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}