// request/response & AI-exchange POJOs
package com.coherentsolutions.coursecrafter.presentation.dto.content;

// ------------ Incoming ingestion payload ------------
public record IngestionRequest(
        String contentType,   // TEXT, URL, YOUTUBE
        String payload        // raw text or URL
) {}
