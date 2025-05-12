//{lectureId, slideId, action, text}
package com.coherentsolutions.coursecrafter.dto;

// ------------ Analyzer -> Updater suggestion ------------
public record SuggestionDto(
        Long lectureId,
        Long slideId,
        Action action,   // ADD, UPDATE, DELETE
        String text,     // new or replacement markdown
        String id        // unique ID for tracking in PR
) {
    public enum Action { ADD, UPDATE, DELETE }
}