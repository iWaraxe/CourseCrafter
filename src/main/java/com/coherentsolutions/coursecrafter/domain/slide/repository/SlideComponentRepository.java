package com.coherentsolutions.coursecrafter.domain.slide.repository;

import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SlideComponentRepository extends JpaRepository<SlideComponent, Long> {
    List<SlideComponent> findBySlideNodeIdOrderByDisplayOrder(Long slideNodeId);

    @Query("SELECT sc FROM SlideComponent sc WHERE sc.slideNode.id = :slideId AND sc.componentType = :type")
    Optional<SlideComponent> findBySlideIdAndType(Long slideId, SlideComponent.ComponentType type);
}