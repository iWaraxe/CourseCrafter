package com.coherentsolutions.coursecrafter.presentation.dto.content;

import java.util.List;

public record LectureDto(Long id, String title, String path, List<SectionDto> sections) {}

