// src/main/java/com/coherentsolutions/coursecrafter/repo/CourseContentRepository.java
package com.coherentsolutions.coursecrafter.repo;

import com.coherentsolutions.coursecrafter.model.CourseContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseContentRepository extends JpaRepository<CourseContent, Long> {
    Optional<CourseContent> findByPath(String path);
}