package com.coherentsolutions.coursecrafter.domain.content.service;

import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentNodeDto;
import com.coherentsolutions.coursecrafter.presentation.dto.content.ContentTreeDto;
import com.coherentsolutions.coursecrafter.model.ContentNode;
import com.coherentsolutions.coursecrafter.model.ContentVersion;
import com.coherentsolutions.coursecrafter.repo.ContentNodeRepository;
import com.coherentsolutions.coursecrafter.repo.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentHierarchyService {

    private final ContentNodeRepository nodeRepository;
    private final ContentVersionRepository versionRepository;

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