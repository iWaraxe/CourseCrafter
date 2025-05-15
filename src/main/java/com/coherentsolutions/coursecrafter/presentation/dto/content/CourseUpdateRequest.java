package com.coherentsolutions.coursecrafter.presentation.dto.content;

import java.time.LocalDate;

// DTO for the request
public record CourseUpdateRequest(
        String content,
        String audience,
        LocalDate reportDate
) {}