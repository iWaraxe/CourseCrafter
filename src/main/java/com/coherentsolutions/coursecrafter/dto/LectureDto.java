package com.coherentsolutions.coursecrafter.dto;

import java.util.List;

public record LectureDto(Long id, String title, String path, List<SectionDto> sections) {}

