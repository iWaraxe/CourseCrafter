package com.coherentsolutions.coursecrafter.domain.legacy.model;
// src/main/java/com/coherentsolutions/coursecrafter/model/CourseContent.java

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "course_content")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseContent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;          // null for top-level lecture
    private String level;           // LECTURE, SECTION, SLIDE, NOTE, etc.
    private String title;

    @Lob @Column(columnDefinition = "text")
    private String markdown;

    private String path;            // e.g. "Lecture1/Section2/Slide5"
    private String gitSha;          // last commit that touched this row
}