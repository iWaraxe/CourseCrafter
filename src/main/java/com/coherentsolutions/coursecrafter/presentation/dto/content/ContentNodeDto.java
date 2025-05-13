package com.coherentsolutions.coursecrafter.presentation.dto.content;

import java.util.ArrayList;
import java.util.List;

// Basic DTOs
public record ContentNodeDto(
        Long id,
        String nodeType,
        String title,
        String description,
        String nodeNumber,
        String path,
        List<ContentNodeDto> children
) {
    public ContentNodeDto(Long id, String nodeType, String title, String description, String nodeNumber, String path) {
        this(id, nodeType, title, description, nodeNumber, path, new ArrayList<>());
    }

    public void setChildren(List<ContentNodeDto> children) {
        this.children.clear();
        this.children.addAll(children);
    }
}