package com.coherentsolutions.coursecrafter.dto;

import java.util.List;

public record SectionDto(Long id, String title, String path, List<SlideDto> slides) {}

