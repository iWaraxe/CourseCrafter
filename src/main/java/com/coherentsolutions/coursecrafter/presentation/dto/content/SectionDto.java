package com.coherentsolutions.coursecrafter.presentation.dto.content;

import com.coherentsolutions.coursecrafter.presentation.dto.slide.SlideDto;

import java.util.List;

public record SectionDto(Long id, String title, String path, List<SlideDto> slides) {}

