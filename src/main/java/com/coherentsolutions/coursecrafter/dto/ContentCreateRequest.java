package com.coherentsolutions.coursecrafter.dto;

public record ContentCreateRequest(
        Long parentId,
        String nodeType,
        String title,
        String description,
        String nodeNumber,
        Integer displayOrder,
        String content
) {}
