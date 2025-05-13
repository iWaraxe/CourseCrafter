package com.coherentsolutions.coursecrafter.presentation.dto.content;

public record ContentCreateRequest(
        Long parentId,
        String nodeType,
        String title,
        String description,
        String nodeNumber,
        Integer displayOrder,
        String content
) {}
