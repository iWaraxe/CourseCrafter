package com.coherentsolutions.coursecrafter.service;

import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.model.ContentVersion;
import com.coherentsolutions.coursecrafter.model.CourseContent;
import com.coherentsolutions.coursecrafter.repo.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.repo.ContentVersionRepository;
import com.coherentsolutions.coursecrafter.repo.CourseContentRepository;
import com.coherentsolutions.coursecrafter.repo.SlideComponentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final CourseContentRepository oldRepo;
    private final ContentNodeRepository nodeRepo;
    private final ContentVersionRepository versionRepo;
    private final SlideComponentRepository componentRepo;

    @Transactional
    public void migrateData() {
        // 1. Create a root course node
        ContentNode courseNode = ContentNode.builder()
                .nodeType(ContentNode.NodeType.COURSE)
                .title("Spring Boot Course")
                .nodeNumber("1")
                .displayOrder(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .path("Course/1")
                .build();

        courseNode = nodeRepo.save(courseNode);

        // 2. Find all lectures (top-level content)
        List<CourseContent> lectures = oldRepo.findAll().stream()
                .filter(c -> c.getParentId() == null || "LECTURE".equals(c.getLevel()))
                .collect(Collectors.toList());

        // 3. Process each lecture
        for (int i = 0; i < lectures.size(); i++) {
            CourseContent lecture = lectures.get(i);

            // Create lecture node
            ContentNode lectureNode = ContentNode.builder()
                    .nodeType(ContentNode.NodeType.LECTURE)
                    .parent(courseNode)
                    .title(lecture.getTitle())
                    .nodeNumber(String.valueOf(i + 1))
                    .displayOrder((i + 1) * 10)
                    .path(courseNode.getPath() + "/Lecture/" + (i + 1))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            lectureNode = nodeRepo.save(lectureNode);

            // Create lecture content version
            if (lecture.getMarkdown() != null && !lecture.getMarkdown().isBlank()) {
                ContentVersion version = ContentVersion.builder()
                        .node(lectureNode)
                        .content(lecture.getMarkdown())
                        .contentFormat("MARKDOWN")
                        .versionNumber(1)
                        .createdAt(LocalDateTime.now())
                        .build();

                versionRepo.save(version);
            }

            // Process children (slides, sections)
            List<CourseContent> children = oldRepo.findAll().stream()
                    .filter(c -> lecture.getId().equals(c.getParentId()))
                    .collect(Collectors.toList());

            migrateChildren(lectureNode, children);
        }
    }

    private void migrateChildren(ContentNode parent, List<CourseContent> children) {
        // Implementation for migrating child nodes
        // Similar to the lecture migration but with proper node types and hierarchy
    }
}