package com.coherentsolutions.coursecrafter.dto;

import java.util.List;

public record CourseStructureDto(String name, List<LectureDto> lectures) {}
