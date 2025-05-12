//parsed “OK / NO / EDIT …” comment
package com.coherentsolutions.coursecrafter.dto;

// ------------ Parsed PR-review command ------------
public record ReviewCommandDto(
        String reviewer,      // GitHub login
        Action action,        // OK, NO, EDIT
        String suggestionId,  // links back to SuggestionDto.id
        String comment        // free-form text (for EDIT)
) {
    public enum Action { OK, NO, EDIT }
}
