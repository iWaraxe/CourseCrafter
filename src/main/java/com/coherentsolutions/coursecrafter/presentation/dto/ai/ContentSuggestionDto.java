package com.coherentsolutions.coursecrafter.presentation.dto.ai;

public record ContentSuggestionDto(
        String targetType,         // "UPDATE" or "NEW"
        String contentType,        // "SCRIPT", "VISUAL", "NOTES", "DEMONSTRATION"
        String targetNodeId,       // ID of existing node to update (null for new content)
        String suggestedLocation,  // Path where to place new content (e.g., "Course/Lecture1/Section2")
        String title,              // Suggested title
        String content,            // The actual content
        String rationale           // Why this change is suggested
) {}
