package com.coherentsolutions.coursecrafter.service;

import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.model.SlideComponent;
import com.coherentsolutions.coursecrafter.repo.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.repo.SlideComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
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

        // Get max display order
        int maxOrder = componentRepository.findBySlideNodeIdOrderByDisplayOrder(slideId).stream()
                .map(SlideComponent::getDisplayOrder)
                .max(Integer::compareTo)
                .orElse(0);

        SlideComponent component = SlideComponent.builder()
                .slideNode(slideNode)
                .componentType(type)
                .content(content)
                .displayOrder(maxOrder + 10) // Leave room between items
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return componentRepository.save(component);
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
     * Convenience method to get or create a component of a specific type
     */
    @Transactional
    public SlideComponent getOrCreateComponent(Long slideId, SlideComponent.ComponentType type, String defaultContent) {
        Optional<SlideComponent> existing = componentRepository.findBySlideIdAndType(slideId, type);

        if (existing.isPresent()) {
            return existing.get();
        } else {
            return createComponent(slideId, type, defaultContent);
        }
    }
}