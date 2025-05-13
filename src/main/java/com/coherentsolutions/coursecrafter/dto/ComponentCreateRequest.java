package com.coherentsolutions.coursecrafter.dto;

public record ComponentCreateRequest(
        String componentType,
        String content
) {}
