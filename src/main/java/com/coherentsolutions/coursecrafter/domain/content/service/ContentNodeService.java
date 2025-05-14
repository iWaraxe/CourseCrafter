package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import com.coherentsolutions.coursecrafter.infrastructure.git.GitCliService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentNodeService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;
    private final GitCliService gitService;
    private final ContentNodeRepository contentNodeRepository;
    private final JdbcTemplate jdbcTemplate;


    @Transactional
    public ContentNode createNode(ContentNode node, String content, String commitMessage) throws IOException, InterruptedException {
        // Set creation timestamp
        if (node.getCreatedAt() == null) {
            node.setCreatedAt(LocalDateTime.now());
        }
        node.setUpdatedAt(LocalDateTime.now());

        // Generate path if parent exists
        if (node.getParent() != null) {
            ContentNode parent = nodeRepository.findById(node.getParent().getId()).orElseThrow();
            String parentPath = parent.getPath() != null ? parent.getPath() : parent.getNodeType() + "/" + parent.getId();
            node.setPath(parentPath + "/" + node.getNodeType() + "-" + UUID.randomUUID().toString().substring(0, 8));
        } else {
            node.setPath(node.getNodeType() + "/" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Save the node first and flush to ensure it's committed
        ContentNode savedNode = nodeRepository.saveAndFlush(node);

        // Create initial version
        if (content != null && !content.isBlank()) {
            ContentVersion version = ContentVersion.builder()
                    .node(savedNode)
                    .content(content)
                    .contentFormat("MARKDOWN")
                    .versionNumber(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            versionRepository.saveAndFlush(version); // Use saveAndFlush
        }

        // Commit to Git
        String branchName = "update-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);

        return savedNode;
    }

    @Transactional
    public ContentNode updateNode(Long nodeId, String newContent, String commitMessage) throws IOException, InterruptedException {
        ContentNode node = nodeRepository.findById(nodeId).orElseThrow();
        node.setUpdatedAt(LocalDateTime.now());

        // Get current version number
        int currentVersion = versionRepository.findLatestVersionByNodeId(nodeId)
                .map(ContentVersion::getVersionNumber)
                .orElse(0);

        // Create new version
        ContentVersion version = ContentVersion.builder()
                .node(node)
                .content(newContent)
                .contentFormat("MARKDOWN")
                .versionNumber(currentVersion + 1)
                .createdAt(LocalDateTime.now())
                .build();
        versionRepository.save(version);

        // Update node in repository
        ContentNode updatedNode = nodeRepository.save(node);

        // Commit to Git
        String branchName = "update-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);

        return updatedNode;
    }

    public Optional<ContentNode> getNodeWithLatestContent(Long nodeId) {
        Optional<ContentNode> nodeOpt = nodeRepository.findById(nodeId);
        if (nodeOpt.isEmpty()) {
            return Optional.empty();
        }

        ContentNode node = nodeOpt.get();
        versionRepository.findLatestVersionByNodeId(nodeId)
                .ifPresent(version -> {
                    // Assuming you have a transient field to hold the content
                    // or you can use a DTO for this purpose
                    node.setMetadataJson("{\"latestContent\": \"" + version.getContent() + "\"}");
                });

        return Optional.of(node);
    }

    @Transactional
    public void deleteNode(Long nodeId, String commitMessage) throws IOException, InterruptedException {
        nodeRepository.deleteById(nodeId);

        // Commit to Git
        String branchName = "delete-" + System.currentTimeMillis();
        gitService.commitAndPush(branchName, commitMessage);
    }

    /**
     * Update display orders for all slides to ensure consistent sequencing
     * across multiple lecture files.
     *
     * This method:
     * 1. Finds all lectures under a course
     * 2. For each lecture, finds all slides underneath it (including those in sections and topics)
     * 3. Reorders all slides sequentially using a global counter
     *
     * @param courseId ID of the course to reorder slides for
     */
    @Transactional
    public void reorderSlidesForCourse(Long courseId) {
        ContentNode course = contentNodeRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Course not found: " + courseId));

        // Get all lectures in order
        List<ContentNode> lectures = contentNodeRepository.findByParentIdOrderByDisplayOrder(courseId);

        int globalSequence = 1000; // Start at 1000 to leave room for manual adjustments

        for (ContentNode lecture : lectures) {
            // Get all slides for this lecture in current order
            List<ContentNode> slides = jdbcTemplate.query(
                    "WITH RECURSIVE node_tree AS (" +
                            "    SELECT id, parent_id, display_order FROM content_node WHERE id = ? " +
                            "  UNION ALL " +
                            "    SELECT n.id, n.parent_id, n.display_order FROM content_node n " +
                            "    JOIN node_tree nt ON n.parent_id = nt.id " +
                            ") " +
                            "SELECT n.* FROM content_node n " +
                            "JOIN node_tree nt ON n.id = nt.id " +
                            "WHERE n.node_type = 'SLIDE' " +
                            "ORDER BY n.display_order",
                    (rs, rowNum) -> {
                        Long nodeId = rs.getLong("id");
                        return contentNodeRepository.findById(nodeId).orElse(null);
                    },
                    lecture.getId()
            );

            // Update global sequence for consistent ordering
            for (ContentNode slide : slides) {
                if (slide != null) {
                    slide.setDisplayOrder(globalSequence);
                    contentNodeRepository.save(slide);
                    globalSequence += 10; // Increment by 10 to leave room
                }
            }
        }

        // Log successful reordering
        log.info("Reordered {} slides across {} lectures for course: {}",
                globalSequence/10 - 100, lectures.size(), course.getTitle());
    }

}