package com.coherentsolutions.coursecrafter.repo;

import com.coherentsolutions.coursecrafter.model.CourseContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CourseContentRepository extends JpaRepository<CourseContent, Long> {
    Optional<CourseContent> findByPath(String path);

    // New method to find all content for a specific course
    @Query("SELECT c FROM CourseContent c WHERE c.path LIKE CONCAT(:courseName, '/%')")
    List<CourseContent> findAllByCourseName(String courseName);
}