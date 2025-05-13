package com.coherentsolutions.coursecrafter.presentation.dto.ai;

// Enhanced proposal DTO with more fields
public record EnhancedProposalDto(
        Long lectureId,           // Target lecture ID
        String lectureTitle,      // For reference
        Long sectionId,           // Target section ID
        String sectionTitle,      // For reference
        Long slideId,             // Target slide ID (null for new slides)
        String slideTitle,        // Title for the slide
        Action action,            // ADD, UPDATE, DELETE
        String originalContent,   // Original content (for UPDATE/DELETE)
        String updatedContent,    // New/updated content
        String rationale,         // Explanation of why this change is needed
        String id                 // Unique ID for tracking
) {}