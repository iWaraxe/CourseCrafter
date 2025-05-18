package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
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

        node.setMarkdownContent(content); // Set content directly


        // Save the node first and flush to ensure it's committed
        ContentNode savedNode = nodeRepository.saveAndFlush(node); // Save the node

        return savedNode;
    }

    @Transactional
    public ContentNode updateNode(Long nodeId, String newContent, String commitMessage) throws IOException, InterruptedException {
        ContentNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("ContentNode not found with id: " + nodeId));

        node.setMarkdownContent(newContent); // Update content directly
        node.setUpdatedAt(LocalDateTime.now());

        // Potentially update other fields like title, description if they are part of the update
        // For example, if newContent contains a new title, you might parse it out and set node.setTitle()

        // Update node in repository
        ContentNode updatedNode = nodeRepository.save(node);

        return updatedNode;
    }

    public Optional<ContentNode> getNodeWithLatestContent(Long nodeId) {
        // Now, the ContentNode itself has the latest content
        return nodeRepository.findById(nodeId);
    }

    @Transactional
    public void deleteNode(Long nodeId, String commitMessage) throws IOException, InterruptedException {
        // Need to handle children if any, or ensure DB constraints do.
        // For example, first delete components associated with this node if it's a SLIDE
        // Then, recursively delete children or let cascade take care of it if configured.
        ContentNode nodeToDelete = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found for deletion: " + nodeId));

        // If it's a slide, its components should be deleted by cascade if ContentNode.slideComponents has CascadeType.ALL
        // Or, explicitly delete them:
        // if (nodeToDelete.getNodeType() == ContentNode.NodeType.SLIDE && nodeToDelete.getSlideComponents() != null) {
        //    slideComponentRepository.deleteAll(nodeToDelete.getSlideComponents());
        // }
        // Handle children recursively or rely on database cascade.

        nodeRepository.deleteById(nodeId);
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