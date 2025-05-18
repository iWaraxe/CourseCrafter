package com.coherentsolutions.coursecrafter.domain.slide.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlideComponentService {

    private final SlideComponentRepository componentRepository;
    private final ContentNodeRepository nodeRepository;
    private final JdbcTemplate jdbcTemplate; // <<<< INJECT JdbcTemplate

    // Optional: If you want to try the re-fetch strategy for debugging createComponent
    // @PersistenceContext
    // private EntityManager entityManager;

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

        String receivedContentPreview = (content != null) ? content.substring(0, Math.min(content.length(), 100)).replace("\n", "\\n") : "null";
        int receivedContentLength = (content != null) ? content.length() : 0;
        log.info("SERVICE.createComponent RECEIVED: slideId={}, type={}, contentLength={}, contentPreview='{}'",
                slideId, type, receivedContentLength, receivedContentPreview);

        SlideComponent componentToSave = SlideComponent.builder()
                .slideNode(slideNode)
                .componentType(type)
                .content(content)
                .displayOrder(maxOrder + 10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        String beforeSavePreview = (componentToSave.getContent() != null) ? componentToSave.getContent().substring(0, Math.min(componentToSave.getContent().length(), 100)).replace("\n", "\\n") : "null";
        int beforeSaveLength = (componentToSave.getContent() != null) ? componentToSave.getContent().length() : 0;
        log.info("SERVICE.createComponent BEFORE SAVE: entityContentLength={}, entityContentPreview='{}'",
                beforeSaveLength, beforeSavePreview);

        SlideComponent savedComponent = null;
        try {
            savedComponent = componentRepository.saveAndFlush(componentToSave); // Using saveAndFlush for immediate DB interaction
        } catch (Exception e) {
            log.error("SERVICE.createComponent ERROR during save for slideId={}, type={}: {}", slideId, type, e.getMessage(), e);
            throw e;
        }

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
        return componentRepository.findById(componentId)
                .map(component -> {
                    component.setContent(newContent);
                    component.setUpdatedAt(LocalDateTime.now());
                    return componentRepository.save(component);
                });
    }

    @Transactional
    public boolean deleteComponent(Long componentId) {
        if (componentRepository.existsById(componentId)) {
            componentRepository.deleteById(componentId);
            return true;
        }
        return false;
    }

    /**
     * Convenience method to get or create a component of a specific type.
     * If the component exists, it's returned. Otherwise, a new one is created
     * with the provided defaultContent.
     */
    @Transactional
    public SlideComponent getOrCreateComponent(Long slideId, SlideComponent.ComponentType type, String defaultContent) {
        log.debug("SERVICE.getOrCreateComponent CALLED for slideId: {}, type: {}, defaultContentPreview: '{}'",
                slideId, type, (defaultContent != null ? defaultContent.substring(0, Math.min(defaultContent.length(), 50)).replace("\n", "\\n") : "null"));

        Optional<SlideComponent> existing = componentRepository.findBySlideIdAndType(slideId, type);

        if (existing.isPresent()) {
            log.debug("SERVICE.getOrCreateComponent: Found existing component ID: {}", existing.get().getId());
            return existing.get();
        } else {
            log.debug("SERVICE.getOrCreateComponent: No existing component found. Creating new one for slideId: {}, type: {}", slideId, type);
            // When creating, it passes `defaultContent` to `createComponent`
            return createComponent(slideId, type, defaultContent);
        }
    }
}