package com.coherentsolutions.coursecrafter.domain.slide.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import jakarta.persistence.EntityNotFoundException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlideService {
    private final ContentNodeRepository contentNodeRepository;

    // Constructor injection

    /**
     * Get all slides for a lecture in sequence order
     */
    public List<ContentNode> getLectureSlides(Long lectureId) {
        ContentNode lecture = contentNodeRepository.findById(lectureId)
                .orElseThrow(() -> new EntityNotFoundException("Lecture not found"));

        // Find all slides under this lecture (directly or via section/topic)
        List<ContentNode> allSlides = contentNodeRepository.findSlidesUnderLecture(lectureId);

        // Sort by display order which comes from the seq tag
        allSlides.sort(Comparator.comparing(ContentNode::getDisplayOrder));

        return allSlides;
    }

    /**
     * Get next and previous slide IDs for navigation
     */
    public Map<String, Long> getSlideNavigation(Long slideId) {
        ContentNode slide = contentNodeRepository.findById(slideId)
                .orElseThrow(() -> new EntityNotFoundException("Slide not found"));

        // Get the lecture this slide belongs to
        ContentNode lecture = findLectureForSlide(slide);

        // Get all slides in order
        List<ContentNode> allSlides = getLectureSlides(lecture.getId());

        // Find current slide index
        int currentIndex = -1;
        for (int i = 0; i < allSlides.size(); i++) {
            if (allSlides.get(i).getId().equals(slideId)) {
                currentIndex = i;
                break;
            }
        }

        Map<String, Long> navigation = new HashMap<>();

        // Add previous slide if not the first
        if (currentIndex > 0) {
            navigation.put("prev", allSlides.get(currentIndex - 1).getId());
        }

        // Add next slide if not the last
        if (currentIndex < allSlides.size() - 1) {
            navigation.put("next", allSlides.get(currentIndex + 1).getId());
        }

        return navigation;
    }

    /**
     * Find the lecture that contains this slide
     */
    private ContentNode findLectureForSlide(ContentNode slide) {
        ContentNode current = slide;

        while (current != null && current.getNodeType() != ContentNode.NodeType.LECTURE) {
            current = current.getParent();
        }

        return current; // Will be the lecture or null if not found
    }
}