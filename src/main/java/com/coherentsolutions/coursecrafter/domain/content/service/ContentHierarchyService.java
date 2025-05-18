package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentNodeDto;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentTreeDto;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentHierarchyService {

    private final ContentNodeRepository nodeRepository;
    private final SlideComponentRepository slideComponentRepository;

    /**
     * Generates a complete hierarchical tree of content
     */
    @Transactional(readOnly = true)
    public ContentTreeDto getContentTree() {
        List<ContentNode> rootNodes = nodeRepository.findByParentIsNullOrderByDisplayOrder();
        List<ContentNodeDto> rootDtos = rootNodes.stream()
                .map(node -> convertToDto(node, true)) // convertToDto will now use node.getMarkdownContent() if needed
                .collect(Collectors.toList());
        return new ContentTreeDto(rootDtos);
    }

    /**
     * Generates a flat outline of all content
     */
    @Transactional(readOnly = true)
    public String generateOutline() {
        List<ContentNode> allNodes = nodeRepository.findAll();
        // Sorting by path is fine
        allNodes.sort((a, b) -> {
            if (a.getPath() == null && b.getPath() == null) return 0;
            if (a.getPath() == null) return -1;
            if (b.getPath() == null) return 1;
            return a.getPath().compareTo(b.getPath());
        });

        return allNodes.stream()
                .map(node -> {
                    int depth = calculateDepth(node.getPath());
                    String indent = "  ".repeat(depth);
                    return indent + "- " + (node.getNodeNumber() != null ? node.getNodeNumber() : "") + " " + node.getTitle();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Generates a text-based outline for LLM context building
     */
    @Transactional(readOnly = true)
    public String generateLlmOutlineContext() {
        // Get all content nodes
        List<ContentNode> allNodes = nodeRepository.findAll();

        StringBuilder builder = new StringBuilder();

        // Build a hierarchical representation
        for (ContentNode node : allNodes) {
            if (node.getNodeType() == ContentNode.NodeType.COURSE) {
                builder.append("# Course: ").append(node.getTitle()).append("\n\n");
            } else if (node.getNodeType() == ContentNode.NodeType.MODULE) {
                builder.append("# Module: ").append(node.getTitle()).append("\n\n");
            } else if (node.getNodeType() == ContentNode.NodeType.LECTURE) {
                builder.append("## Lecture ").append(node.getNodeNumber()).append(": ")
                        .append(node.getTitle()).append("\n\n");
            } else if (node.getNodeType() == ContentNode.NodeType.SECTION) {
                builder.append("### Section ").append(node.getNodeNumber()).append(" ")
                        .append(node.getTitle()).append("\n\n");
            } else if (node.getNodeType() == ContentNode.NodeType.TOPIC) {
                builder.append("#### Topic ").append(node.getNodeNumber()).append(" ")
                        .append(node.getTitle()).append("\n\n");
            } else if (node.getNodeType() == ContentNode.NodeType.SLIDE) {
                builder.append("##### Slide ").append(node.getNodeNumber()).append(" ")
                        .append(node.getTitle()).append("\n\n");
            }
        }

        return builder.toString();
    }

    /**
     * Generates a text-based outline for a specific course
     */
    @Transactional(readOnly = true)
    public String generateLlmOutlineContextForCourse(String courseName) {
        // Get all content nodes related to this course
        List<ContentNode> courseNodes = nodeRepository.findByPathPattern(courseName + "/%");

        StringBuilder builder = new StringBuilder();
        builder.append("# Course: ").append(courseName).append("\n\n");

        // Build a hierarchical representation for this specific course
        // Similar to the generateLlmOutlineContext method but filtered by course

        return builder.toString();
    }

    /**
     * Generates a detailed text-based outline with minimal component info
     */
    public String generateDetailedOutlineWithComponents(String courseName) {
        // Build a hierarchical representation
        StringBuilder builder = new StringBuilder();
        builder.append("# Course: ").append(courseName).append("\n\n");

        // First get the course node
        ContentNode courseNode = nodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                .stream()
                .filter(node -> node.getTitle().equals(courseName) || courseName.equals("BasicAiCourse"))
                .findFirst()
                .orElse(null);

        if (courseNode == null) {
            log.warn("No course node found with name: {}", courseName);
            return builder.toString(); // Return just the course title
        }

        // Now get all lectures (direct children of course)
        List<ContentNode> lectures = nodeRepository.findByParentIdOrderByDisplayOrder(courseNode.getId());

        for (ContentNode lecture : lectures) {
            builder.append("## Lecture ").append(lecture.getTitle()).append("\n\n");

            // Get sections under this lecture
            List<ContentNode> sections = nodeRepository.findByParentIdOrderByDisplayOrder(lecture.getId());

            for (ContentNode section : sections) {
                // For sections, format properly with nodeNumber and clean title
                String sectionTitle = section.getTitle();
                String nodeNumber = section.getNodeNumber();

                // Clean up the title if it starts with the numeric prefix
                if (sectionTitle.matches("^\\d+\\.\\d+\\.\\s+.*")) {
                    sectionTitle = sectionTitle.replaceFirst("^\\d+\\.\\d+\\.\\s+", "");
                }

                builder.append("### Section ").append(nodeNumber).append(". ").append(sectionTitle).append("\n\n");

                // Get topics under this section
                List<ContentNode> topics = nodeRepository.findByParentIdOrderByDisplayOrder(section.getId());

                for (ContentNode topic : topics) {
                    // For topics, similar cleaning
                    String topicTitle = topic.getTitle();
                    String topicNumber = topic.getNodeNumber();

                    // Clean up the title if it starts with the numeric prefix
                    if (topicTitle.matches("^\\d+\\.\\d+\\.\\d+\\.\\s+.*")) {
                        topicTitle = topicTitle.replaceFirst("^\\d+\\.\\d+\\.\\d+\\.\\s+", "");
                    }

                    builder.append("#### Topic ").append(topicNumber).append(". ").append(topicTitle).append("\n\n");

                    // Get slides under this topic
                    List<ContentNode> slides = nodeRepository.findByParentIdOrderByDisplayOrder(topic.getId());

                    for (ContentNode slide : slides) {
                        builder.append("##### Slide ").append(slide.getNodeNumber()).append(": ").append(slide.getTitle()).append("\n\n");

                        // Only list component types, without trying to access content
                        try {
                            List<SlideComponent> components = slideComponentRepository.findBySlideNodeIdOrderByDisplayOrder(slide.getId());
                            if (!components.isEmpty()) {
                                builder.append("###### Components: ");
                                for (int i = 0; i < components.size(); i++) {
                                    builder.append(components.get(i).getComponentType());
                                    if (i < components.size() - 1) {
                                        builder.append(", ");
                                    }
                                }
                                builder.append("\n\n");
                            }
                        } catch (Exception e) {
                            log.error("Error retrieving components for slide {}: {}", slide.getId(), e.getMessage());
                            // Don't append error message to the builder
                        }
                    }
                }
            }
        }

        return builder.toString();
    }

    /**
     * Generates a detailed text-based outline with slide components
     */
    @Transactional(readOnly = true)
    public String generateDetailedOutlineContext(String courseName) {
        // Build a hierarchical representation
        StringBuilder builder = new StringBuilder();
        builder.append("# Course: ").append(courseName).append("\n\n");

        // First get the course node
        ContentNode courseNode = nodeRepository.findByNodeType(ContentNode.NodeType.COURSE)
                .stream()
                .filter(node -> node.getTitle().equals(courseName) || courseName.equals("BasicAiCourse"))
                .findFirst()
                .orElse(null);

        if (courseNode == null) {
            log.warn("No course node found with name: {}", courseName);
            return builder.toString(); // Return just the course title
        }

        // Now get all lectures (direct children of course)
        List<ContentNode> lectures = nodeRepository.findByParentIdOrderByDisplayOrder(courseNode.getId());

        for (ContentNode lecture : lectures) {
            // For lectures, use the title directly which should already contain the number
            builder.append("## Lecture ").append(lecture.getTitle()).append("\n\n");

            // Get sections under this lecture
            List<ContentNode> sections = nodeRepository.findByParentIdOrderByDisplayOrder(lecture.getId());

            for (ContentNode section : sections) {
                // For sections, format properly with nodeNumber and clean title
                String sectionTitle = section.getTitle();
                String nodeNumber = section.getNodeNumber();

                // Clean up the title if it starts with the numeric prefix
                if (sectionTitle.matches("^\\d+\\.\\d+\\.\\s+.*")) {
                    sectionTitle = sectionTitle.replaceFirst("^\\d+\\.\\d+\\.\\s+", "");
                }

                builder.append("### Section ").append(nodeNumber).append(". ").append(sectionTitle).append("\n\n");

                // Get topics under this section
                List<ContentNode> topics = nodeRepository.findByParentIdOrderByDisplayOrder(section.getId());

                for (ContentNode topic : topics) {
                    // For topics, similar cleaning
                    String topicTitle = topic.getTitle();
                    String topicNumber = topic.getNodeNumber();

                    // Clean up the title if it starts with the numeric prefix
                    if (topicTitle.matches("^\\d+\\.\\d+\\.\\d+\\.\\s+.*")) {
                        topicTitle = topicTitle.replaceFirst("^\\d+\\.\\d+\\.\\d+\\.\\s+", "");
                    }

                    builder.append("#### Topic ").append(topicNumber).append(". ").append(topicTitle).append("\n\n");

                    // Get slides under this topic
                    List<ContentNode> slides = nodeRepository.findByParentIdOrderByDisplayOrder(topic.getId());

                    for (ContentNode slide : slides) {
                        builder.append("##### Slide ").append(slide.getNodeNumber()).append(": ").append(slide.getTitle()).append("\n\n");

                        // For the detailed outline, we don't include component content
                        // Just list that the slide exists, without trying to access components
                    }
                }
            }
        }

        return builder.toString();
    }

    @Transactional(readOnly = true)
    private ContentNodeDto convertToDto(ContentNode node, boolean includeChildren) {
        ContentNodeDto dto = new ContentNodeDto(
                node.getId(),
                node.getNodeType().toString(),
                node.getTitle(),
                node.getDescription(),
                node.getNodeNumber(),
                node.getPath(),
                // Pass new ArrayList to constructor that takes children
                new java.util.ArrayList<>()
        );

        if (includeChildren) {
            List<ContentNode> children = nodeRepository.findByParentIdOrderByDisplayOrder(node.getId());
            List<ContentNodeDto> childDtos = children.stream()
                    .map(child -> convertToDto(child, true))
                    .collect(Collectors.toList());
            dto.setChildren(childDtos); // Use the setter
        }
        // If ContentNodeDto needs markdownContent, add it here:
        // dto.setMarkdownContent(node.getMarkdownContent());
        return dto;
    }

    private int calculateDepth(String path) {
        if (path == null) return 0;
        return (int) path.chars().filter(ch -> ch == '/').count();
    }
}