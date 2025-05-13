package com.coherentsolutions.coursecrafter.presentation.dto.content;

import java.util.List;

public record CourseStructureDto(String name, List<LectureDto> lectures) {}
