package com.coherentsolutions.coursecrafter.presentation.dto.ai;

// AI-related DTOs
public record AiProposalDto(
        Long targetNodeId,
        Long parentNodeId,
        String nodeType,
        String action,
        String title,
        String nodeNumber,
        String content,
        String rationale,
        Integer displayOrder
) {}
