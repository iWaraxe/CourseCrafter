package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.domain.slide.model.SlideComponent;
import com.coherentsolutions.coursecrafter.domain.slide.repository.SlideComponentRepository;
import com.coherentsolutions.coursecrafter.domain.slide.service.SlideService;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentNodeDto;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentTreeDto;
import com.coherentsolutions.coursecrafter.domain.content.model.ContentNode;
import com.coherentsolutions.coursecrafter.domain.version.model.ContentVersion;
import com.coherentsolutions.coursecrafter.domain.content.repository.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.domain.version.repository.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentHierarchyService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;
    private final SlideComponentRepository slideComponentRepository;

    /**
     * Generates a complete hierarchical tree of content
     */
    public ContentTreeDto getContentTree() {
        // Get all root nodes (courses)
        List<ContentNode> rootNodes = nodeRepository.findByParentIsNullOrderByDisplayOrder();

        // Convert to DTOs with full hierarchy
        List<ContentNodeDto> rootDtos = rootNodes.stream()
                .map(node -> convertToDto(node, true))
                .collect(Collectors.toList());

        return new ContentTreeDto(rootDtos);
    }

    /**
     * Generates a flat outline of all content
     */
    public String generateOutline() {
        List<ContentNode> allNodes = nodeRepository.findAll();

        return allNodes.stream()
                .sorted((a, b) -> a.getPath().compareTo(b.getPath()))
                .map(node -> {
                    int depth = calculateDepth(node.getPath());
                    String indent = "  ".repeat(depth);
                    return indent + "- " + node.getNodeNumber() + " " + node.getTitle();
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Generates a text-based outline for LLM context building
     */
    public String generateLlmOutlineContext() {
        // Get all content nodes
        List<ContentNode> allNodes = nodeRepository.findAll();

        // Get latest content for each node
        Map<Long, String> latestContents = versionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        v -> v.getNode().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber())),
                                optVersion -> optVersion.map(ContentVersion::getContent).orElse("")
                        )
                ));

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

                // For slides, include the content
                String content = latestContents.get(node.getId());
                if (content != null && !content.isBlank()) {
                    builder.append("```markdown\n").append(content).append("\n```\n\n");
                }
            }
        }

        return builder.toString();
    }

    /**
     * Generates a text-based outline for a specific course
     */
    public String generateLlmOutlineContextForCourse(String courseName) {
        // Get all content nodes related to this course
        List<ContentNode> courseNodes = nodeRepository.findByPathPattern(courseName + "/%");

        // Get latest content for each node
        Map<Long, String> latestContents = versionRepository.findAll().stream()
                .filter(v -> courseNodes.stream().anyMatch(n -> n.getId().equals(v.getNode().getId())))
                .collect(Collectors.groupingBy(
                        v -> v.getNode().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy((v1, v2) -> v1.getVersionNumber().compareTo(v2.getVersionNumber())),
                                optVersion -> optVersion.map(ContentVersion::getContent).orElse("")
                        )
                ));

        StringBuilder builder = new StringBuilder();
        builder.append("# Course: ").append(courseName).append("\n\n");

        // Build a hierarchical representation for this specific course
        // Similar to the generateLlmOutlineContext method but filtered by course

        return builder.toString();
    }

    /**
     * Generates a detailed text-based outline with slide components
     */
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
            builder.append("## Lecture ").append(lecture.getNodeNumber() != null ? lecture.getNodeNumber() : "")
                    .append(": ").append(lecture.getTitle()).append("\n\n");

            // Get sections under this lecture
            List<ContentNode> sections = nodeRepository.findByParentIdOrderByDisplayOrder(lecture.getId());

            for (ContentNode section : sections) {
                builder.append("### Section ").append(section.getNodeNumber() != null ? section.getNodeNumber() : "")
                        .append(": ").append(section.getTitle()).append("\n\n");

                // Get topics under this section
                List<ContentNode> topics = nodeRepository.findByParentIdOrderByDisplayOrder(section.getId());

                for (ContentNode topic : topics) {
                    builder.append("#### Topic ").append(topic.getNodeNumber() != null ? topic.getNodeNumber() : "")
                            .append(": ").append(topic.getTitle()).append("\n\n");

                    // Get slides under this topic
                    List<ContentNode> slides = nodeRepository.findByParentIdOrderByDisplayOrder(topic.getId());

                    for (ContentNode slide : slides) {
                        builder.append("##### Slide ").append(slide.getNodeNumber() != null ? slide.getNodeNumber() : "")
                                .append(": ").append(slide.getTitle()).append("\n\n");

                        // Include slide components
                        try {
                            List<SlideComponent> components = slideComponentRepository.findBySlideNodeIdOrderByDisplayOrder(slide.getId());
                            if (!components.isEmpty()) {
                                for (SlideComponent comp : components) {
                                    builder.append("###### ").append(comp.getComponentType()).append("\n");
                                    String content = comp.getContent();
                                    if (content != null && !content.isBlank()) {
                                        builder.append(content.substring(0, Math.min(50, content.length()))).append("...\n\n");
                                    } else {
                                        builder.append("(Empty content)\n\n");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error retrieving components for slide {}: {}", slide.getId(), e.getMessage());
                            builder.append("(Component data error)\n\n");
                        }
                    }
                }
            }
        }

        return builder.toString();
    }

    private ContentNodeDto convertToDto(ContentNode node, boolean includeChildren) {
        ContentNodeDto dto = new ContentNodeDto(
                node.getId(),
                node.getNodeType().toString(),
                node.getTitle(),
                node.getDescription(),
                node.getNodeNumber(),
                node.getPath()
        );

        if (includeChildren) {
            List<ContentNode> children = nodeRepository.findByParentIdOrderByDisplayOrder(node.getId());
            List<ContentNodeDto> childDtos = children.stream()
                    .map(child -> convertToDto(child, true))
                    .collect(Collectors.toList());
            dto.setChildren(childDtos);
        }

        return dto;
    }

    private int calculateDepth(String path) {
        if (path == null) return 0;
        return (int) path.chars().filter(ch -> ch == '/').count();
    }
}