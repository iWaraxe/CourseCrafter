package com.coherentsolutions.coursecrafter.presentation.dto.ai;

import java.util.List;

public record SummarizationResponseDto(
        List<ContentSuggestionDto> suggestions
) {}