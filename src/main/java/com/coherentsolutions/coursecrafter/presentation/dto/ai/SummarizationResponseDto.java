package com.coherentsolutions.coursecrafter.presentation.dto.ai;

public record SummarizationResponseDto(
        List<ContentSuggestionDto> suggestions
) {}