// request/response & AI-exchange POJOs
package com.coherentsolutions.coursecrafter.presentation.dto.content;

import java.time.LocalDate;

// ------------ Incoming ingestion payload ------------
public record IngestionRequest(
        String contentType,   // TEXT, URL, YOUTUBE
        String payload,       // raw text or URL
        String audience,      // target audience (optional)
        LocalDate reportDate, // date of the report (optional)
        String courseName     // target course (optional)
) {
    // Constructor that provides defaults for backward compatibility
    public IngestionRequest(String contentType, String payload) {
        this(contentType, payload, null, null, null);
    }
}