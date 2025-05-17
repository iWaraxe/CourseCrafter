package com.coherentsolutions.coursecrafter.domain.slide.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // <<<< ADD SLF4J FOR LOGGING
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j // <<<< ADD SLF4J FOR LOGGING
public class SlideComponentService {

    private final SlideComponentRepository componentRepository;
    private final ContentNodeRepository nodeRepository;

    public List<SlideComponent> getComponentsForSlide(Long slideId) {
        return componentRepository.findBySlideNodeIdOrderByDisplayOrder(slideId);
    }

    @Transactional
    public SlideComponent createComponent(Long slideId, SlideComponent.ComponentType type, String content) {
        ContentNode slideNode = nodeRepository.findById(slideId)
                .orElseThrow(() -> new IllegalArgumentException("Slide not found: " + slideId));

        if (slideNode.getNodeType() != ContentNode.NodeType.SLIDE) {
            throw new IllegalArgumentException("Node is not a slide: " + slideId);
        }

        int maxOrder = componentRepository.findBySlideNodeIdOrderByDisplayOrder(slideId).stream()
                .map(SlideComponent::getDisplayOrder)
                .max(Integer::compareTo)
                .orElse(0);

        // CRITICAL LOGGING POINT 1: What content string is the service receiving?
        String receivedContentPreview = (content != null) ? content.substring(0, Math.min(content.length(), 100)).replace("\n", "\\n") : "null";
        int receivedContentLength = (content != null) ? content.length() : 0;
        log.info("SERVICE.createComponent RECEIVED: slideId={}, type={}, contentLength={}, contentPreview='{}'",
                slideId, type, receivedContentLength, receivedContentPreview);

        SlideComponent componentToSave = SlideComponent.builder()
                .slideNode(slideNode)
                .componentType(type)
                .content(content) // This is where the string from the parser is assigned
                .displayOrder(maxOrder + 10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // CRITICAL LOGGING POINT 2: What is in the entity JUST BEFORE save?
        String beforeSavePreview = (componentToSave.getContent() != null) ? componentToSave.getContent().substring(0, Math.min(componentToSave.getContent().length(), 100)).replace("\n", "\\n") : "null";
        int beforeSaveLength = (componentToSave.getContent() != null) ? componentToSave.getContent().length() : 0;
        log.info("SERVICE.createComponent BEFORE SAVE: entityContentLength={}, entityContentPreview='{}'",
                beforeSaveLength, beforeSavePreview);

        SlideComponent savedComponent = null;
        try {
            savedComponent = componentRepository.save(componentToSave);
        } catch (Exception e) {
            log.error("SERVICE.createComponent ERROR during save for slideId={}, type={}: {}", slideId, type, e.getMessage(), e);
            throw e; // Re-throw to see if transaction rolls back
        }


        // CRITICAL LOGGING POINT 3: What is in the entity JUST AFTER save?
        String afterSavePreview = "null";
        int afterSaveLength = 0;
        if (savedComponent != null && savedComponent.getContent() != null) {
            afterSavePreview = savedComponent.getContent().substring(0, Math.min(savedComponent.getContent().length(), 100)).replace("\n", "\\n");
            afterSaveLength = savedComponent.getContent().length();
        }
        log.info("SERVICE.createComponent AFTER SAVE: savedComponentId={}, savedContentLength={}, savedContentPreview='{}'",
                (savedComponent != null ? savedComponent.getId() : "null"),
                afterSaveLength, afterSavePreview);

        return savedComponent;
    }

    @Transactional
    public Optional<SlideComponent> updateComponent(Long componentId, String newContent) {
        // ... (existing code)
        return componentRepository.findById(componentId)
                .map(component -> {
                    component.setContent(newContent);
                    component.setUpdatedAt(LocalDateTime.now());
                    return componentRepository.save(component);
                });
    }

    @Transactional
    public boolean deleteComponent(Long componentId) {
        // ... (existing code)
        if (componentRepository.existsById(componentId)) {
            componentRepository.deleteById(componentId);
            return true;
        }
        return false;
    }

    @Transactional
    public SlideComponent getOrCreateComponent(Long slideId, SlideComponent.ComponentType type, String defaultContent) {
        // ... (existing code)
        Optional<SlideComponent> existing = componentRepository.findBySlideIdAndType(slideId, type);

        if (existing.isPresent()) {
            return existing.get();
        } else {
            return createComponent(slideId, type, defaultContent);
        }
    }
}